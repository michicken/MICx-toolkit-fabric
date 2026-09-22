package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * RemoteShop（远程商店 / 远程买弹）：不开界面也能点到远处商店。
 *
 * <p>两件事：
 * <ol>
 *   <li>把<b>客户端</b>准星射程从原版实体 3 格 / 方块 4.5 格抬到 5.5 格
 *       （{@link dev.micx.micxfabric.mixin.MixinPlayerInteractionRange}），自己瞄着右键就够得着；</li>
 *   <li>「买一次」：扫附近名字含关键词（默认 refill / ammo）的全息盔甲架，取最近的一个，
 *       自绘一条 5.5 格射线（命中谁就是谁：实体优先，退而求其次打方块）并右键。</li>
 * </ol>
 *
 * <p><b>硬上限在服务端，不在客户端</b>（26.2 原版字节码实测，2026-09-15）：服务端只接受
 * 眼球到实体碰撞箱 &lt; 6 格、到方块 &lt; 5.5 格的交互，超出<b>静默丢包</b>，插件连事件都收不到。
 * 所以超过 6 格时模块不会发（面板直接告诉你还差几格），任何客户端手法都过不去。
 *
 * <p>安全边界：不自动发、不循环发——只有按面板按钮/快捷键那一下才发一次，1 秒冷却；
 * 目标不在射程内就只报告不发。
 */
public final class RemoteShopModule implements Module {
    private static final RemoteShopModule INSTANCE = new RemoteShopModule();
    /** B = buy；可被面板改成别的键或清空。 */
    private static final int DEFAULT_KEY = GLFW.GLFW_KEY_B;
    /** 扫描结果缓存：面板每帧都画，不能每帧重扫实体表。 */
    private static final long SCAN_CACHE_MS = 1_000L;

    /**
     * 三把枪的固定槽位（用户定稿 2026-09-22）：物品栏槽位 2/3/4 = 快捷栏索引 1/2/3。
     * 枪一→槽位2、枪二→槽位3、枪三→槽位4；只可改快捷键，不可改槽位。
     */
    private static final int[] BUY_SLOTS = {1, 2, 3};
    /** 买弹序列每个阶段续期的外部保护窗时长（毫秒）：覆盖到下一阶段刷新。 */
    private static final long BUY_HOLD_MS = 400L;
    /** 发完购买/交互包并切回原枪后，键盘连点再被按住的尾巴（用户定稿：50 毫秒）。 */
    private static final long BUY_TAIL_MS = 50L;

    /** 一条候选：全息文字、实体类型、距离、是否命中关键词、实体 id（触发时按 id 找回）。 */
    public record Candidate(int entityId, String name, String kind, double distance, boolean matched, Vec3 position) {
        public String kindLabel() {
            return kind == null || kind.isBlank() ? "实体" : kind;
        }
    }

    private String keywordsRaw = RemoteShopRules.keywordsText(RemoteShopRules.DEFAULT_KEYWORDS);
    private List<String> keywords = RemoteShopRules.DEFAULT_KEYWORDS;
    private InputBinding binding = new InputBinding(DEFAULT_KEY);
    private boolean enabled;
    private boolean configLoaded;
    private long lastTriggerMs = -1L;
    private String lastReport = "还没触发过";
    private List<Candidate> cachedScan = List.of();
    private long cachedScanMs = -1L;

    /* ---- 三槽买弹（按下激活，2026-09-22）：切到枪N → 对商店发包 → 切回原槽 ---- */
    /** 每把枪一个组合键（1–3 键，2026-09-23 起支持组合录制）；空数组 = 未绑定。 */
    private final int[][] buyKeys = new int[3][KeyChord.MAX_KEYS];
    /** 各枪组合键的「全按下」边沿状态（照 HotkeyRuntime.handleChord 的语义）。 */
    private final boolean[] buyChordPrev = new boolean[3];
    /** 0 = 空闲；1 = 待切槽；2 = 已切槽待发包；3 = 已发包待切回。 */
    private int buyStage;
    private int buyGun = -1;
    private int buyRestoreSlot = -1;
    private Candidate buyTarget;

    private RemoteShopModule() {
    }

    public static RemoteShopModule instance() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return "remote_shop";
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean value) {
        loadConfig();
        enabled = value;
        ModuleStateStore.put(id(), value);
    }

    @Override
    public InputBinding primaryBinding() {
        loadConfig();
        return binding;
    }

    /** 快捷键=买一次（模块关着的话 HotkeyRuntime 会先把它打开，射程提升同时生效）。 */
    @Override
    public void onPrimaryPressed(Minecraft client, boolean newlyEnabled) {
        if (newlyEnabled && client != null && client.player != null) {
            client.player.sendSystemMessage(ChatMessageStyles.notice("RemoteShop ON"));
        }
        triggerNearest(client);
    }

    /**
     * 三槽买弹（按下激活，非开关；模块关着时无效——用户定稿 2026-09-22）：
     * 组合键全按下的上升沿 → 范围内有目标就切到枪 N → 对商店自绘射线发包 → 切回原槽。
     * 切槽前一刻到发包完成后 50ms，键盘连点被外部保护窗完全按住。
     */
    @Override
    public void tick(Minecraft client) {
        loadConfig();
        if (buyStage != 0) {
            advanceBuy(client);
            return;
        }
        if (client == null || client.player == null || client.level == null) return;
        boolean blocked = !enabled || client.gui.screen() != null || client.isPaused();
        for (int gun = 0; gun < buyKeys.length; gun++) {
            if (KeyChord.isEmpty(buyKeys[gun])) {
                buyChordPrev[gun] = false;
                continue;
            }
            // 边沿状态无论是否 blocked 都要推进（GUI 里按下的组合不跨屏触发），
            // 触发只发生在未屏蔽时的上升沿——与 HotkeyRuntime.handleChord 同语义。
            boolean down = KeyChord.isAllDown(buyKeys[gun], client);
            boolean old = buyChordPrev[gun];
            buyChordPrev[gun] = down;
            if (blocked || !down || old) continue;
            beginBuy(client, gun);
            return;
        }
    }

    /** 触发前置检查（冷却/目标/射程）：任何一条不过都不切槽不发包，只报告 + 聊天提示。 */
    private void beginBuy(Minecraft client, int gun) {
        long now = System.currentTimeMillis();
        String label = "枪" + (gun + 1);
        if (!RemoteShopRules.due(now, lastTriggerMs)) {
            notify(client, label + "买弹：冷却中（1 秒一次）");
            return;
        }
        Candidate target = firstMatched(client);
        if (target == null) {
            notify(client, label + "买弹：附近 " + Math.round(RemoteShopRules.SCAN_RADIUS)
                    + " 格内没有关键词目标");
            return;
        }
        if (!RemoteShopRules.withinTriggerRange(target.distance())) {
            notify(client, String.format(Locale.ROOT, "%s买弹：目标在 %.1f 格外，没发",
                    label, target.distance()));
            return;
        }
        Entity entity = client.level.getEntity(target.entityId());
        if (entity == null) {
            notify(client, label + "买弹：目标跑掉了，再按一次试试");
            return;
        }
        lastTriggerMs = now;
        buyGun = gun;
        buyTarget = target;
        buyRestoreSlot = client.player.getInventory().getSelectedSlot();
        buyStage = 1;
        KeyboardClickerModule.instance().holdExternalFor(BUY_HOLD_MS);
    }

    /** 三阶段推进（照 SkillCast 的节奏：切槽 → 发包 → 切回，各占一 tick）。 */
    private void advanceBuy(Minecraft client) {
        if (client == null || client.player == null || client.level == null
                || client.gui.screen() != null || client.isPaused()) {
            abortBuy(client);
            return;
        }
        String label = "枪" + (buyGun + 1);
        if (buyStage == 1) {
            KeyboardClickerModule.instance().holdExternalFor(BUY_HOLD_MS);
            client.player.getInventory().setSelectedSlot(BUY_SLOTS[buyGun]);
            buyStage = 2;
            return;
        }
        if (buyStage == 2) {
            KeyboardClickerModule.instance().holdExternalFor(BUY_HOLD_MS);
            Entity entity = client.level.getEntity(buyTarget.entityId());
            String result = entity == null
                    ? "目标跑掉了，没发"
                    : fire(client, entity, buyTarget);
            notify(client, label + "：" + result);
            // 键盘连点开着：交互包发出即进入 50ms 放行倒计时，不再等切回原槽
            // （用户定稿 2026-09-22，与技能释放"动作包完成即恢复"同一节奏）。
            if (KeyboardClickerModule.instance().enabled()) {
                KeyboardClickerModule.instance().holdExternalFor(BUY_TAIL_MS);
            }
            buyStage = 3;
            return;
        }
        // stage 3：切回原枪；键盘连点关着时保护窗再续 50ms（开着的已在发包后放行）
        if (buyRestoreSlot >= 0 && buyRestoreSlot < 9) {
            client.player.getInventory().setSelectedSlot(buyRestoreSlot);
        }
        if (!KeyboardClickerModule.instance().enabled()) {
            KeyboardClickerModule.instance().holdExternalFor(BUY_TAIL_MS);
        }
        buyStage = 0;
        buyGun = -1;
        buyTarget = null;
        buyRestoreSlot = -1;
    }

    /** 序列中断（开界面/暂停/换世界）：恢复原槽、清状态、立刻放行键盘连点。 */
    private void abortBuy(Minecraft client) {
        if (buyStage >= 1 && buyStage <= 3 && client != null && client.player != null
                && buyRestoreSlot >= 0 && buyRestoreSlot < 9) {
            client.player.getInventory().setSelectedSlot(buyRestoreSlot);
        }
        buyStage = 0;
        buyGun = -1;
        buyTarget = null;
        buyRestoreSlot = -1;
    }

    private Candidate firstMatched(Minecraft client) {
        for (Candidate candidate : scan(client, true)) {
            if (candidate.matched()) return candidate;
        }
        return null;
    }

    private void notify(Minecraft client, String message) {
        report(message);
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(ChatMessageStyles.notice(message));
        }
    }

    @Override
    public void resetState() {
        cachedScan = List.of();
        cachedScanMs = -1L;
        abortBuy(Minecraft.getInstance());
        java.util.Arrays.fill(buyChordPrev, false);
    }

    /**
     * 扫附近带自定义名的实体——Hypixel 的全息就是「不可见盔甲架 + CustomName」，
     * 所以「有没有名字」本身就是最省事的目标筛选。按距离排序。
     */
    public List<Candidate> scan(Minecraft client, boolean force) {
        loadConfig();
        if (client == null || client.player == null || client.level == null) return List.of();
        long now = System.currentTimeMillis();
        if (!force && cachedScanMs >= 0L && now - cachedScanMs < SCAN_CACHE_MS) return cachedScan;
        List<Candidate> found = new ArrayList<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == null || entity == client.player || !entity.hasCustomName()) continue;
            String name = entity.getCustomName() == null ? "" : entity.getCustomName().getString();
            if (name.isBlank()) continue;
            double distance = Math.sqrt(entity.distanceToSqr(client.player));
            if (distance > RemoteShopRules.SCAN_RADIUS) continue;
            found.add(new Candidate(entity.getId(), name, entity.getClass().getSimpleName(), distance,
                    RemoteShopRules.matches(name, keywords), entity.position()));
        }
        found.sort(Comparator.comparingDouble(Candidate::distance));
        cachedScan = List.copyOf(found);
        cachedScanMs = now;
        return cachedScan;
    }

    /** 面板/快捷键「买一次」：朝最近的目标发一次右键交互，返回给面板看的人话。 */
    public String triggerNearest(Minecraft client) {
        loadConfig();
        if (client == null || client.player == null || client.level == null) return report("世界未加载");
        long now = System.currentTimeMillis();
        if (!RemoteShopRules.due(now, lastTriggerMs)) return report("冷却中（1 秒一次）");
        Candidate target = null;
        for (Candidate candidate : scan(client, true)) {
            if (candidate.matched()) {
                target = candidate;
                break;
            }
        }
        if (target == null) {
            return report("附近 " + Math.round(RemoteShopRules.SCAN_RADIUS) + " 格内没有名字含「"
                    + RemoteShopRules.keywordsText(keywords) + "」的东西");
        }
        if (!RemoteShopRules.withinTriggerRange(target.distance())) {
            return report(String.format(Locale.ROOT, "最近目标「%s」在 %.1f 格外｜%s｜没发",
                    squeeze(target.name()), target.distance(),
                    RemoteShopRules.distanceVerdict(target.distance())));
        }
        Entity entity = client.level.getEntity(target.entityId());
        if (entity == null) return report("目标跑掉了，再按一次试试");
        lastTriggerMs = now;
        return report(fire(client, entity, target));
    }

    /**
     * 自绘射线：朝着全息的位置打一条 5.5 格射线，命中谁就交互谁。
     * 先实体后方块（和原版准星的取舍一致：谁近算谁，这里以实体优先），
     * 这样不用事先知道 Hypixel 那边把可点判定挂在盔甲架还是方块上。
     */
    private String fire(Minecraft client, Entity entity, Candidate target) {
        LocalPlayer player = client.player;
        Vec3 eye = player.getEyePosition();
        Vec3 aim = entity.getBoundingBox().getCenter();
        Vec3 direction = aim.subtract(eye);
        if (direction.lengthSqr() < 1.0E-4D) return "目标贴脸，直接右键就行";
        Vec3 unit = direction.normalize();
        Vec3 end = eye.add(unit.scale(RemoteShopRules.CLIENT_RANGE));
        AABB sweep = player.getBoundingBox().expandTowards(unit.scale(RemoteShopRules.CLIENT_RANGE)).inflate(1.0D);
        double limit = RemoteShopRules.CLIENT_RANGE * RemoteShopRules.CLIENT_RANGE;
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, end, sweep,
                candidate -> candidate != player && candidate.isPickable() && !candidate.isSpectator(), limit);
        if (entityHit != null) {
            client.gameMode.interact(player, entityHit.getEntity(), entityHit, InteractionHand.MAIN_HAND);
            player.swing(InteractionHand.MAIN_HAND);
            return String.format(Locale.ROOT, "已对「%s」（%s · %.1f 格）发右键",
                    squeeze(target.name()), entityHit.getEntity().getClass().getSimpleName(), target.distance());
        }
        BlockHitResult blockHit = client.level.clip(new ClipContext(eye, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, blockHit);
            player.swing(InteractionHand.MAIN_HAND);
            return String.format(Locale.ROOT, "全息前没实体，改对方块 %s 右键（%.1f 格）",
                    blockHit.getBlockPos().toShortString(), target.distance());
        }
        return "射线一路打空：目标可能在墙后或更远，没发";
    }

    private String report(String message) {
        lastReport = message;
        return lastReport;
    }

    /** 全息文字里常带颜色/换行，面板里压成一行短的。 */
    private static String squeeze(String name) {
        if (name == null) return "";
        String flat = name.replaceAll("§.", "").replaceAll("\\s+", " ").trim();
        return flat.length() <= 28 ? flat : flat.substring(0, 28) + "…";
    }

    public String lastReport() {
        return lastReport;
    }

    // ---- 配置 ----

    public List<String> keywords() {
        loadConfig();
        return keywords;
    }

    /** 面板编辑框里的一行关键词文本。 */
    public String keywordsRaw() {
        loadConfig();
        return keywordsRaw;
    }

    public void setKeywords(String raw) {
        loadConfig();
        keywordsRaw = raw == null ? "" : raw.trim();
        keywords = RemoteShopRules.parseKeywords(keywordsRaw);
        cachedScanMs = -1L;
        saveConfig();
    }

    public int bindingCode() {
        loadConfig();
        return binding.code();
    }

    public void setBindingCode(int code) {
        loadConfig();
        binding = new InputBinding(code);
        saveConfig();
    }

    /** 枪 N（0 起）的买弹组合键；空数组 = 未绑定。 */
    public int[] buyKeyCodes(int gun) {
        loadConfig();
        if (gun < 0 || gun >= buyKeys.length) return KeyChord.EMPTY;
        return buyKeys[gun].clone();
    }

    public void setBuyKeyCodes(int gun, int[] codes) {
        loadConfig();
        if (gun < 0 || gun >= buyKeys.length) return;
        buyKeys[gun] = KeyChord.normalize(codes);
        saveConfig();
    }

    /** 枪 N 的固定槽位（快捷栏索引）：枪一=槽位2(1)、枪二=槽位3(2)、枪三=槽位4(3)。 */
    public static int buySlot(int gun) {
        return gun >= 0 && gun < BUY_SLOTS.length ? BUY_SLOTS[gun] : -1;
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path current = FabricRuntime.configPath().resolve("remote-shop.properties");
        Properties properties = ConfigProperties.load(current, null);
        keywordsRaw = ConfigProperties.string(properties, "keywords", keywordsRaw);
        keywords = RemoteShopRules.parseKeywords(keywordsRaw);
        binding = new InputBinding(ConfigProperties.integer(properties, "keyCode", DEFAULT_KEY,
                -108, GLFW.GLFW_KEY_LAST));
        for (int gun = 0; gun < buyKeys.length; gun++) {
            // 优先新组合键字段（逗号串），为空时迁移旧单键字段
            buyKeys[gun] = KeyChord.readConfig(properties, "buyKeys" + (gun + 1), "buyKey" + (gun + 1), 0);
        }
    }

    private void saveConfig() {
        loadConfig();
        Properties properties = new Properties();
        properties.setProperty("keywords", keywordsRaw);
        properties.setProperty("keyCode", Integer.toString(binding.code()));
        for (int gun = 0; gun < buyKeys.length; gun++) {
            KeyChord.writeConfig(properties, "buyKeys" + (gun + 1), "buyKey" + (gun + 1), buyKeys[gun]);
        }
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("remote-shop.properties"), properties,
                    "MICx RemoteShop configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save RemoteShop configuration", exception);
        }
    }
}
