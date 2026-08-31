package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * 队友 HP 面板 — 对齐 Forge 最新卡片设计（v2.33+）：
 * 无底板卡 + 右下投影、左竖条 accent（倒地紫 > 自己蓝 > 血量分级）、★ 自己置顶、
 * 绝对血量刻度血条（全队最大血池基准）、金盾叠加、手持图标（剑按格挡语义换皮）、
 * 潜行红石块、开枪橙色闪点、右侧技能列（LR/Heal 冷却 + LR 命中徽章）、QUIT/DEAD/DOWN 幽灵卡。
 *
 * <p>与 Forge 的差异：fr 二救冷却来自聊天事件时间戳（frCooldownUntil），与 Forge 等价；
 * ReviveHolo 救援进度（盔甲架 holo 扫描）暂缓。</p>
 */
public final class TeammateHpModule implements Module {
    private static final TeammateHpModule INSTANCE = new TeammateHpModule();
    private static final int CARD_HEIGHT = 26;
    private static final int CARD_GAP = 2;
    private static final int ACCENT_W = 3;
    private static final int PAD = 6;
    private static final int RIGHT_PAD = 6;
    private static final int BAR_H = 6;
    private static final int DEFAULT_KEY = GLFW.GLFW_KEY_H;

    private boolean enabled;
    private boolean active = true;
    private boolean overlayEnabled = true;
    private boolean showHidden;
    private boolean showDistance = true;
    /** Revive Timer 模式：B=读盔甲架 holo 救援计时（服务端真值）；A=25s 本地估算。 */
    private boolean reviveHoloB = true;
    private int cardWidth = 180;
    private int bgAlpha = 70;
    private int screenX = 8;
    private int screenY = 40;
    private float uiScale = 1.0f;
    private int keyCode = DEFAULT_KEY;
    private boolean configLoaded;
    private Object activeLevel;

    /** 队友 max 血量缓存，防止服务器 max 更新滞后（只升不降）。 */
    private final Map<String, Float> knownMax = new HashMap<>();
    /** M-7：tab 名单空缓存兜底。 */
    private Set<String> lastTabNamesCache;

    /**
     * 本机独立的槽位 5（Lightning Rod / Heal）采样器。
     * 不喂 TeamSyncModule 的采样器 —— 它只在入房后的 sendState 中被观察，
     * 单人或未入房时永远为空。这里自建采样，逐 tick 采集本地槽位 5。
     */
    private final TeamSkillTracker localSkill = new TeamSkillTracker();
    private int lastSlot5Level = Integer.MIN_VALUE;

    private TeammateHpModule() {
    }

    public static TeammateHpModule instance() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return "teammate_hp";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        loadConfig();
        this.enabled = enabled;
        if (!enabled) activeLevel = null;
        ModuleStateStore.put(id(), enabled);
    }

    @Override
    public InputBinding primaryBinding() {
        loadConfig();
        return new InputBinding(keyCode);
    }

    @Override
    public void onPrimaryPressed(Minecraft client, boolean newlyEnabled) {
        loadConfig();
        if (!newlyEnabled || !active) active = !active;
        saveConfig();
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(ChatMessageStyles.notice("TeammateHP: " + (active ? "ON" : "OFF")));
        }
    }

    @Override
    public void resetState() {
        activeLevel = null;
    }

    @Override
    public void tick(Minecraft client) {
        if (client == null || client.level == null || client.player == null) {
            activeLevel = null;
            return;
        }
        // 本机槽位 5（技能）逐 tick 采样，驱动自己的 LR/HEAL 冷却倒计时
        if (lastSlot5Level != System.identityHashCode(client.level)) {
            lastSlot5Level = System.identityHashCode(client.level);
            clearWorldCaches();
        }
        ItemStack stack = client.player.getInventory().getItem(TeamSkillTracker.SLOT_INDEX);
        String ready = null;
        boolean grayDye = false;
        int count = -1;
        if (stack != null && !stack.isEmpty()) {
            ready = TeamSkillTracker.canonicalSkillName(stack.getHoverName().getString());
            count = stack.getCount();
            // 实测：冷却时 Hypixel 把槽位物品换成 dye（名字仍 "X Skill"，count 递减）
            grayDye = stack.getItem() instanceof net.minecraft.world.item.DyeItem;
        }
        localSkill.observe(ready, grayDye, count, System.currentTimeMillis());
        activeLevel = client.level;
    }

    public boolean active() {
        return active;
    }

    public boolean overlayEnabled() {
        return overlayEnabled;
    }

    public boolean showHidden() {
        return showHidden;
    }

    public boolean showDistance() {
        return showDistance;
    }

    public int cardWidth() {
        return cardWidth;
    }

    public int bgAlpha() {
        return bgAlpha;
    }

    public int screenX() {
        return screenX;
    }

    public int screenY() {
        return screenY;
    }

    public float uiScale() {
        return uiScale;
    }

    public int keyCode() {
        loadConfig();
        return keyCode;
    }

    public void setActive(boolean value) {
        active = value;
        saveConfig();
    }

    public void setOverlayEnabled(boolean value) {
        overlayEnabled = value;
        saveConfig();
    }

    public void setShowHidden(boolean value) {
        showHidden = value;
        saveConfig();
    }

    public void setShowDistance(boolean value) {
        showDistance = value;
        saveConfig();
    }

    public boolean isReviveHoloB() {
        return reviveHoloB;
    }

    public void setReviveHoloB(boolean value) {
        reviveHoloB = value;
        if (!value) ReviveHoloTracker.get().clear();
        saveConfig();
    }

    public void setCardWidth(int value) {
        cardWidth = clamp(value, 100, 400);
        saveConfig();
    }

    public void setBgAlpha(int value) {
        bgAlpha = clamp(value, 0, 200);
        saveConfig();
    }

    public void setScreenX(int value) {
        screenX = clamp(value, 0, 9_999);
        saveConfig();
    }

    public void setScreenY(int value) {
        screenY = clamp(value, 0, 9_999);
        saveConfig();
    }

    public void setLayoutPosition(int x, int y) {
        loadConfig();
        screenX = clamp(x, 0, 9_999);
        screenY = clamp(y, 0, 9_999);
    }

    public void saveLayoutConfiguration() {
        loadConfig();
        saveConfig();
    }

    public void setUiScale(float value) {
        uiScale = clamp(value, 0.5f, 1.75f);
        saveConfig();
    }

    public void setKeyCode(int value) {
        keyCode = value == 0 ? GLFW.GLFW_KEY_UNKNOWN : clamp(value, -108, GLFW.GLFW_KEY_LAST);
        saveConfig();
    }

    private void clearWorldCaches() {
        knownMax.clear();
        lastTabNamesCache = null;
        localSkill.reset();
        ReviveHoloTracker.get().clear();
    }

    public void drawHud(GuiGraphicsExtractor graphics) {
        if (!enabled || !active || !overlayEnabled) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.player == null) return;
        long now = System.currentTimeMillis();

        // 名单：Zombies 内优先计分板常驻名单（行固定、尸体 NPC 天然进不来），
        // 否则退回 tab∩实体。playerStatuses 即 Forge roster 的等价物。
        ZombiesTracker tracker = ZombiesTracker.instance();
        Map<String, String> statuses = tracker.isInZombies()
                ? tracker.frame().playerStatuses() : Map.of();
        boolean useRoster = !statuses.isEmpty();

        Set<String> allow = useRoster ? statuses.keySet() : tabNamesNow(client);
        Map<String, AbstractClientPlayer> ents = new HashMap<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof AbstractClientPlayer player) || player.isRemoved()) continue;
            if (player != client.player) {
                if (!allow.contains(player.getName().getString())) continue;
                if (!showHidden && player.isInvisible()) continue;
            }
            ents.put(player.getName().getString(), player);
        }

        List<String> names = new ArrayList<>(useRoster ? allow : ents.keySet());
        String selfName = client.player.getName().getString();
        if (!names.contains(selfName)) names.add(selfName);
        names.sort(String.CASE_INSENSITIVE_ORDER);
        names.remove(selfName);
        names.add(0, selfName);
        while (names.size() > 4) names.remove(names.size() - 1);

        // 共享快照索引（只读 all()，不创建条目）
        Map<String, TeamSyncSnapshot> peers = new HashMap<>();
        for (TeamSyncSnapshot snapshot : TeamSyncModule.instance().state().all()) {
            peers.put(snapshot.name, snapshot);
        }

        float scaleX = HudLayoutRegistry.scaleX("teammate_hp", uiScale);
        float scaleY = HudLayoutRegistry.scaleY("teammate_hp", uiScale);
        int x = Math.round(screenX / scaleX);
        int y = Math.round(screenY / scaleY);

        // B 模式：每帧驱动 holo 扫描（内部 100ms 节流），DOWN 卡用服务端救援真值；A 模式跳过
        if (reviveHoloB) ReviveHoloTracker.get().tick(client, tracker);

        // 绝对血量刻度基准 = 全队最大血池（1 HP = 相同像素）
        float scaleMax = 20f;
        for (AbstractClientPlayer player : ents.values()) {
            scaleMax = Math.max(scaleMax, knownMaxFor(player));
        }

        graphics.pose().pushMatrix();
        graphics.pose().scale(scaleX, scaleY);
        try {
            for (String name : names) {
                String st = statuses.get(name);
                boolean down = "down".equals(st);
                AbstractClientPlayer player = ents.get(name);
                if ("quit".equals(st)) {
                    drawQuitGhost(graphics, name, x, y);
                } else if ("dead".equals(st) && !down) {
                    drawDeadGhost(graphics, name, x, y);
                } else if (down && player == null) {
                    drawDownGhost(graphics, client, tracker, name, x, y, now);
                } else if (player != null) {
                    drawTeammateHp(graphics, client, tracker, player, now, x, y, scaleMax, down);
                } else {
                    drawAliveGhost(graphics, peers.get(name), name, x, y, now);
                }
                drawSkillStatus(graphics, client, peers, name, x, y, now);
                y += CARD_HEIGHT + CARD_GAP;
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    /**
     * 获取并缓存该实体的最大血量。Hypixel 对 maxHealth 更新有延迟，常出现 hp > reportedMax；
     * 取 max(reportedMax, cached)，hp 超出且无吸收时用 hp 兜底，只升不降。
     */
    private float knownMaxFor(AbstractClientPlayer player) {
        String name = player.getName().getString();
        float reported = player.getMaxHealth();
        float hp = player.getHealth();
        float abs = player.getAbsorptionAmount();
        Float cached = knownMax.get(name);
        float max = cached != null ? Math.max(cached, reported) : reported;
        if (hp > max && abs <= 0f) max = hp;
        knownMax.put(name, max);
        return max;
    }

    /** tab 名单（带空名单缓存兜底）。 */
    private Set<String> tabNamesNow(Minecraft client) {
        Set<String> names = new HashSet<>();
        if (client.getConnection() != null) {
            for (PlayerInfo info : client.getConnection().getOnlinePlayers()) {
                names.add(info.getProfile().name());
            }
        }
        if (names.isEmpty() && lastTabNamesCache != null && !lastTabNamesCache.isEmpty()) {
            return lastTabNamesCache;
        }
        lastTabNamesCache = new HashSet<>(names);
        return names;
    }

    /** 倒地状态文案：REVIVING 服务端真值 / REVIVE 本地出血估算 / DOWN 无数据。 */
    private static String downStatusText(ReviveHoloTracker.Info holo, double downLeftSec) {
        if (holo != null && holo.state() == ReviveHoloTracker.State.REVIVING) {
            return String.format(java.util.Locale.ROOT, "REVIVING %.1fs", holo.seconds());
        }
        if (holo != null && holo.state() == ReviveHoloTracker.State.WAITING) {
            return String.format(java.util.Locale.ROOT, "REVIVE %.1fs", downLeftSec);
        }
        return String.format(java.util.Locale.ROOT, "DOWN %.1fs", downLeftSec);
    }

    /** 倒地状态颜色：REVIVING 水青 / WAITING 绿 / 其余紫。 */
    private static int downStatusColor(ReviveHoloTracker.Info holo) {
        if (holo != null && holo.state() == ReviveHoloTracker.State.REVIVING) return 0xFF55DDFF;
        if (holo != null && holo.state() == ReviveHoloTracker.State.WAITING) return 0xFF55DD55;
        return 0xFFB05CFF;
    }

    /** 倒地底槽比例：REVIVING = 服务端救援进度；其余 = 本地出血估算。 */
    private static float downBarRatio(ReviveHoloTracker.Info holo, long downLeftMs) {
        if (holo != null && holo.state() == ReviveHoloTracker.State.REVIVING) {
            return ReviveHoloRules.reviveProgress(holo.totalSec(), holo.seconds());
        }
        return Math.max(0, downLeftMs) / (float) DOWN_BLEEDOUT_MS;
    }

    /** 投影阴影 + 左色条 + 名字的公共片段。返回 statusRightX（技能列预留后的右边界）。 */
    private int drawCardBase(GuiGraphicsExtractor graphics, Minecraft client,
                             String name, int x, int y, int accentColor, int nameColor) {
        int shadowAlpha = bgAlpha * 255 / 200;
        graphics.fill(x - 1, y + 1, x + cardWidth + 1, y + CARD_HEIGHT + 1, shadowAlpha << 24);
        graphics.fill(x, y, x + ACCENT_W, y + CARD_HEIGHT, accentColor);
        String dn = trimName(client, name, (cardWidth - ACCENT_W - PAD - RIGHT_PAD) * 55 / 100);
        graphics.text(client.font, dn, x + ACCENT_W + PAD, y + 5, nameColor, true);
        return statusRight(client, name, x, cardWidth, RIGHT_PAD);
    }

    /** 血槽左端公共 X。 */
    private static int contentLeft(int x) {
        return x + ACCENT_W + PAD;
    }

    private void drawQuitGhost(GuiGraphicsExtractor graphics, String name, int x, int y) {
        Minecraft client = Minecraft.getInstance();
        int right = drawCardBase(graphics, client, name, x, y, 0xFF3A3A3A, 0xFF666666);
        graphics.text(client.font, "QUIT", right - client.font.width("QUIT"), y + 5, 0xFF777777, true);
        int barY = y + CARD_HEIGHT - BAR_H - 5;
        graphics.fill(contentLeft(x), barY, right, barY + BAR_H, 0x99000000);
    }

    private void drawDeadGhost(GuiGraphicsExtractor graphics, String name, int x, int y) {
        Minecraft client = Minecraft.getInstance();
        int right = drawCardBase(graphics, client, name, x, y, 0xFF555555, 0xFF888888);
        graphics.text(client.font, "DEAD", right - client.font.width("DEAD"), y + 5, 0xFFAA4444, true);
        int barY = y + CARD_HEIGHT - BAR_H - 5;
        graphics.fill(contentLeft(x), barY, right, barY + BAR_H, 0x99000000);
    }

    /** 倒地且实体被服务器换成尸体 NPC：紫条占位卡，出血倒计时来自聊天 KNOCK 事件时间戳。 */
    private void drawDownGhost(GuiGraphicsExtractor graphics, Minecraft client, ZombiesTracker tracker,
                               String name, int x, int y, long now) {
        int right = drawCardBase(graphics, client, name, x, y, 0xFFB05CFF, 0xFFD9A8FF);
        long downLeftMs = downLeftMs(tracker, name, now);
        ReviveHoloTracker.Info holo = reviveHoloB ? ReviveHoloTracker.get().get(name) : null;
        String text = downStatusText(holo, downLeftMs / 1000.0);
        graphics.text(client.font, text, right - client.font.width(text), y + 5,
                downStatusColor(holo), true);
        int barY = y + CARD_HEIGHT - BAR_H - 5;
        int contentX = contentLeft(x);
        graphics.fill(contentX, barY, right, barY + BAR_H, 0x99000000);
        int filled = (int) ((right - contentX) * downBarRatio(holo, downLeftMs));
        if (filled > 0) {
            graphics.fill(contentX, barY, contentX + filled, barY + BAR_H,
                    holo.state() == ReviveHoloTracker.State.REVIVING ? 0xFF55DDFF : 0xFFB05CFF);
        }
    }

    private static final long DOWN_BLEEDOUT_MS = 25_000L;

    /** 倒地出血剩余毫秒；无 KNOCK 时间戳时返回满窗。 */
    private static long downLeftMs(ZombiesTracker tracker, String name, long now) {
        Long since = tracker.eventState().downSince().get(name);
        if (since == null || since <= 0L) return DOWN_BLEEDOUT_MS;
        return Math.max(0L, DOWN_BLEEDOUT_MS - (now - since));
    }

    /** 存活但实体未加载（远距/切图）：共享快照回填血量，占位不消失。 */
    private void drawAliveGhost(GuiGraphicsExtractor graphics, TeamSyncSnapshot snap,
                                String name, int x, int y, long now) {
        Minecraft client = Minecraft.getInstance();
        int right = drawCardBase(graphics, client, name, x, y, 0xFF777777, 0xFFCCCCCC);
        int contentX = contentLeft(x);
        int barY = y + CARD_HEIGHT - BAR_H - 5;
        if (snap != null && snap.hp >= 0
                && (snap.isSkillFresh(10_000L) || now - snap.receivedMs < 10_000L)) {
            float ratio = snap.maxHp > 0
                    ? Math.max(0, Math.min(1, snap.hp / snap.maxHp))
                    : (snap.hp > 0 ? 1f : 0f);
            float absRatio = snap.maxHp > 0 && snap.absorption > 0 ? Math.max(0, snap.absorption / snap.maxHp) : 0f;
            String hpText = (int) (snap.hp + snap.absorption) + "/" + (int) (snap.maxHp > 0 ? snap.maxHp : snap.hp);
            int hpColor = snap.absorption > 0 ? 0xFFFFD700
                    : (snap.hp >= 12 ? 0xFF55FF55 : snap.hp >= 8 ? 0xFFFFFF55 : 0xFFFF5555);
            graphics.text(client.font, hpText, right - client.font.width(hpText), y + 5, hpColor, true);
            int barW = right - contentX;
            graphics.fill(contentX, barY, right, barY + BAR_H, 0x99000000);
            if (ratio >= 0) {
                int filled = (int) (barW * ratio);
                if (filled > 0) graphics.fill(contentX, barY, contentX + filled, barY + BAR_H, hpColor);
                if (absRatio > 0) {
                    int absW = (int) (barW * absRatio);
                    int absStart = contentX + filled;
                    graphics.fill(absStart, barY, Math.min(right, absStart + absW), barY + BAR_H, 0xFFFFD700);
                }
                graphics.fill(contentX, barY, right, barY + 1, 0x22FFFFFF);
            }
        } else {
            graphics.text(client.font, "--", right - client.font.width("--"), y + 5, 0xFF888888, true);
            graphics.fill(contentX, barY, right, barY + BAR_H, 0x99000000);
        }
    }

    /**
     * 极简现代卡片 — 左色条 + ★自己 + 手持图标（剑格挡换皮）/潜行红石块/开枪闪点 +
     * 右对齐 HP + 距离 + 绝对刻度血条 + 金盾叠加。
     */
    private void drawTeammateHp(GuiGraphicsExtractor graphics, Minecraft client, ZombiesTracker tracker,
                                AbstractClientPlayer player, long now, int x, int y, float scaleMax, boolean isDown) {
        String name = player.getName().getString();
        float hp = player.getHealth();
        float max = knownMaxFor(player);
        float abs = player.getAbsorptionAmount();
        float totalDisplay = hp + abs;
        float ratio = max > 0 ? Math.max(0, hp / max) : 0;
        boolean isSelf = player == client.player;

        int contentX = contentLeft(x);
        int textY = y + 5;
        int barY = y + CARD_HEIGHT - BAR_H - 5;
        int shadowAlpha = bgAlpha * 255 / 200;
        graphics.fill(x - 1, y + 1, x + cardWidth + 1, y + CARD_HEIGHT + 1, shadowAlpha << 24);

        // Accent strip — 倒地紫 > 自己蓝 > 血量分级
        int accentColor = isDown ? 0xFFB05CFF : (isSelf ? 0xFF55AAFF : hpBarColor(hp));
        graphics.fill(x, y, x + ACCENT_W, y + CARD_HEIGHT, accentColor);

        // Name — 自己加星标；有图标时预留更多空间
        ItemStack held = player.getMainHandItem();
        boolean isSneaking = player.isCrouching();
        boolean isFiring = tracker.isFiring(name, now);
        // 近战剑语义化（v0.2.11 统一谓词，FR-5）：格挡中 = 钻石剑，拿刀不格挡 = 木剑。
        // 格挡判定三层：① 26.2 use 态 + 副手/主手剑（本地经 MixinItemStack 进 BLOCK，
        // 远程 1.8 blocking flag 经 Via 映射为 using item）② 原生 isBlocking（持盾）兜底。
        ItemStack iconStack = held;
        if (held != null && !held.isEmpty() && held.getItem().builtInRegistryHolder().is(ItemTags.SWORDS)) {
            iconStack = new ItemStack(isBlockingLike(player) ? Items.DIAMOND_SWORD : Items.WOODEN_SWORD);
        }
        boolean hasIcons = (iconStack != null && !iconStack.isEmpty()) || isSneaking;
        int contentW = cardWidth - ACCENT_W - PAD - RIGHT_PAD;
        int nameMaxW = contentW * (hasIcons ? 40 : 55) / 100;
        String displayName = trimName(client, (isSelf ? "★ " : "") + name, nameMaxW);
        int nameColor = isDown ? 0xFFD9A8FF : (isSelf ? 0xFF88CCFF : 0xFFF2F2F2);
        graphics.text(client.font, displayName, contentX, textY, nameColor, true);

        // State icons — 手持物品 / 红石块(潜行)；开枪时图标右上角橙色闪点
        if (hasIcons) {
            int iconX = contentX + client.font.width(displayName) + 3;
            int iconY = textY - 2;
            int heldIconX = -1;
            if (iconStack != null && !iconStack.isEmpty()) {
                graphics.item(iconStack, iconX, iconY);
                heldIconX = iconX;
                iconX += 18;
            }
            if (isSneaking) {
                graphics.item(new ItemStack(Blocks.REDSTONE_BLOCK), iconX, iconY);
            }
            if (heldIconX >= 0 && isFiring) {
                graphics.fill(heldIconX + 11, iconY, heldIconX + 16, iconY + 5, 0xFFFF9900);
            }
        }

        // HP number — 右对齐；倒地显示救援/出血倒计时而不是被重置的假满血
        long downLeftMs = isDown ? downLeftMs(tracker, name, now) : 0L;
        String hpText;
        int hpColor;
        ReviveHoloTracker.Info holo = isDown && reviveHoloB ? ReviveHoloTracker.get().get(name) : null;
        if (isDown) {
            hpText = downStatusText(holo, downLeftMs / 1000.0);
            hpColor = downStatusColor(holo);
        } else {
            hpText = (int) totalDisplay + "/" + (int) max;
            if (abs > 0) {
                hpColor = 0xFFFFCC44;
            } else if (hp >= 12f) {
                hpColor = 0xFF55DD55;
            } else if (hp >= 8f) {
                hpColor = 0xFFEEEE55;
            } else {
                hpColor = 0xFFFF5555;
            }
        }
        int statusRightX = statusRight(client, name, x, cardWidth, RIGHT_PAD);
        int hpW = client.font.width(hpText);
        graphics.text(client.font, hpText, statusRightX - hpW, textY, hpColor, true);

        // Distance — HP 左侧灰字；fr 二救冷却窗口内优先显示 fr 剩余（对齐 Forge）
        Long frUntil = tracker.eventState().frCooldownUntil().get(name);
        long frMs = frUntil == null ? 0L : Math.max(0L, frUntil - now);
        if ((showDistance || frMs > 0) && !isSelf) {
            if (frMs > 0 && !isDown) {
                String frText = String.format(java.util.Locale.ROOT, "fr %.1fs", frMs / 1000f);
                int frW = client.font.width(frText);
                int frX = statusRightX - hpW - 6 - frW;
                if (frX >= contentX) {
                    graphics.text(client.font, frText, frX, textY, 0xFFFFCC44, true);
                }
            }
        }
        if (showDistance && !isSelf) {
            String distText = String.format("+%.1fm", client.player.distanceTo(player));
            int distW = client.font.width(distText);
            int distX = statusRightX - hpW - 6 - distW;
            if (distX >= contentX) {
                graphics.text(client.font, distText, distX, textY, 0xFF999999, false);
            }
        }

        // Bottom bar — 绝对血量刻度：底槽长度 = 自己血池/全队最大血池；
        // 倒地时底槽拉满当出血倒计时用（ratio 已换为剩余时间比例）
        float barRatio = isDown ? downLeftMs / (float) DOWN_BLEEDOUT_MS : Math.min(ratio, 1.0f);
        int troughW = isDown ? Math.max(10, statusRightX - contentX)
                : Math.max(10, (int) (contentW * (max / scaleMax)));
        graphics.fill(contentX, barY, contentX + troughW, barY + BAR_H, 0x99000000);
        float effectiveBarRatio = isDown ? downBarRatio(holo, downLeftMs) : barRatio;
        int filled = (int) (troughW * effectiveBarRatio);
        if (filled > 0) {
            graphics.fill(contentX, barY, contentX + filled, barY + BAR_H,
                    isDown ? (holo != null && holo.state() == ReviveHoloTracker.State.REVIVING
                            ? 0xFF55DDFF : 0xFFB05CFF) : hpBarColor(hp));
            graphics.fill(contentX, barY, contentX + filled, barY + 1, 0x40FFFFFF);
            if (!isDown && hp < 6f) {
                graphics.fill(Math.max(contentX, contentX + filled - 2), barY, contentX + filled, barY + BAR_H, 0xFFFF7777);
            }
        }

        // Absorption shield — 金色叠加，同一绝对刻度
        if (abs > 0 && !isDown) {
            int absFilled = (int) (contentW * (abs / scaleMax));
            int absEnd = Math.min(contentX + filled + absFilled, contentX + contentW + PAD);
            if (absEnd > contentX + filled) {
                graphics.fill(contentX + filled, barY, absEnd, barY + BAR_H, 0xFFFFCC44);
            }
        }
    }

    /** 技能快照：自己读独立采样器（teamsync 的 LR 精确通道优先）；队友读共享快照 + LR 通道覆盖。 */
    private TeamSkillTracker.Snapshot skillSnapshot(Map<String, TeamSyncSnapshot> peers,
                                                    String name, long now) {
        Minecraft client = Minecraft.getInstance();
        if (name != null && client.player != null && name.equals(client.player.getName().getString())) {
            TeamSkillTracker.Snapshot local = localSkill.snapshot(now);
            TeamSyncModule sync = TeamSyncModule.instance();
            if (sync.enabled()) {
                TeamSkillTracker.Snapshot net = sync.localSkillSnapshot(now);
                if (net != null && net.known && "Lightning Rod".equals(net.skillName)) {
                    local = net;   // teamsync 的 LR 精确倒计时优先
                }
            }
            return local;
        }
        TeamSyncSnapshot snapshot = peers.get(name);
        if (snapshot == null || !snapshot.isSkillFresh(10_000L)) return null;
        if (!snapshot.skillKnown || snapshot.skillName == null) return null;
        TeamSkillTracker.State state = snapshot.skillState;
        int remain = snapshot.skillRemainingSeconds;
        // v2.33+：LR 冷却用释放通道精确截止时间倒计时；已就绪则置 READY
        if ("Lightning Rod".equals(snapshot.skillName) && snapshot.lrKnown && snapshot.isLrFresh(10_000L)) {
            if (snapshot.isLrReady(now)) {
                state = TeamSkillTracker.State.READY;
            } else {
                state = TeamSkillTracker.State.COOLING;
                remain = (int) snapshot.lrRemainingSec(now);
            }
        }
        return TeamSkillTracker.Snapshot.of(state, snapshot.skillName, remain);
    }

    private int skillReservedWidth(Minecraft client, Map<String, TeamSyncSnapshot> peers,
                                   String name, long now) {
        TeamSkillTracker.Snapshot skill = skillSnapshot(peers, name, now);
        if (skill == null || !skill.known || skill.skillName == null) return 0;
        boolean isLr = "Lightning Rod".equals(skill.skillName);
        String text = skill.isCooling()
                ? (isLr ? "LR " : "Heal ") + skill.remainingSeconds
                : (isLr ? "LR" : "Heal");
        int w = client.font.width(text) + 8;
        TeamSyncSnapshot snap = peers.get(name);
        if (snap != null && snap.isLrHitFresh(6_000L) && isLr) {
            String badge = lrBadge(snap);
            if (!badge.isEmpty()) w += client.font.width(badge) + 4;
        }
        return w;
    }

    private int statusRight(Minecraft client, String name, int x, int cardW, int rightPad) {
        Map<String, TeamSyncSnapshot> peers = new HashMap<>();
        for (TeamSyncSnapshot snapshot : TeamSyncModule.instance().state().all()) {
            peers.put(snapshot.name, snapshot);
        }
        return x + cardW - rightPad - skillReservedWidth(client, peers, name, System.currentTimeMillis());
    }

    private static String lrBadge(TeamSyncSnapshot snap) {
        return snap.lastLrStruckCount >= 0 ? "×" + snap.lastLrStruckCount
                : (snap.lastLrStruckName != null ? "·" + snap.lastLrStruckName : "");
    }

    /** 右侧技能列：冷却 "LR N"/"Heal N"（红/黄）；就绪 "LR"/"Heal"（水青）；LR 命中徽章 6s 内。 */
    private void drawSkillStatus(GuiGraphicsExtractor graphics, Minecraft client,
                                 Map<String, TeamSyncSnapshot> peers, String name,
                                 int x, int y, long now) {
        TeamSkillTracker.Snapshot skill = skillSnapshot(peers, name, now);
        if (skill == null || !skill.known || skill.skillName == null) return;
        boolean isLr = "Lightning Rod".equals(skill.skillName);
        int right = x + cardWidth - RIGHT_PAD;
        String mainText = skill.isCooling()
                ? (isLr ? "LR " : "Heal ") + skill.remainingSeconds
                : (isLr ? "LR" : "Heal");
        int mainColor = skill.isCooling() ? (isLr ? 0xFFFF5555 : 0xFFFFCC44) : 0xFF55DDFF;
        int mainW = client.font.width(mainText);
        graphics.fill(right - mainW - 4, y + 3, right + 2, y + 13, 0x22111111);
        graphics.text(client.font, mainText, right - mainW, y + 5, mainColor, true);
        if (isLr) {
            TeamSyncSnapshot snap = peers.get(name);
            if (snap != null && snap.isLrHitFresh(6_000L)) {
                String badge = lrBadge(snap);
                if (!badge.isEmpty()) {
                    int badgeColor = snap.lastLrStruckCount >= 0 ? 0xFFFF5555 : 0xFFAA88FF;
                    int badgeW = client.font.width(badge);
                    graphics.text(client.font, badge, right - mainW - badgeW - 6, y + 5, badgeColor, true);
                }
            }
        }
    }

    /** 名字按像素宽截断（超出补 …）。 */
    private static String trimName(Minecraft client, String name, int maxW) {
        String dn = name;
        if (client.font.width(dn) > maxW) {
            while (dn.length() > 1 && client.font.width(dn + "…") > maxW) {
                dn = dn.substring(0, dn.length() - 1);
            }
            dn += "…";
        }
        return dn;
    }

    /** 血量分级颜色：≥12 绿，≥8 黄，<8 红。 */
    private static int hpBarColor(float hp) {
        if (hp >= 12f) return 0xFF55DD55;
        if (hp >= 8f) return 0xFFEEEE55;
        return 0xFFFF5555;
    }

    /**
     * 格挡统一谓词（FR-5，spec AC-9）：
     * use 态且使用中物品为剑（1.8 剑格挡经 Via/本地 mixin 表现为 using-item）
     * 或 26.2 原生持盾 isBlocking()。
     */
    static boolean isBlockingLike(net.minecraft.world.entity.LivingEntity player) {
        if (player.isBlocking()) return true;
        if (player.isUsingItem()) {
            ItemStack useItem = player.getUseItem();
            return useItem != null && !useItem.isEmpty()
                    && useItem.getItem().builtInRegistryHolder().is(ItemTags.SWORDS);
        }
        return false;
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path current = FabricRuntime.configPath().resolve("teammate-hp.properties");
        Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_TeammateHP.cfg");
        Properties properties = ConfigProperties.load(current, legacy);
        overlayEnabled = ConfigProperties.bool(properties, "overlayEnabled", true);
        showHidden = ConfigProperties.bool(properties, "showHidden", false);
        showDistance = ConfigProperties.bool(properties, "showDistance", true);
        reviveHoloB = ConfigProperties.bool(properties, "reviveHoloB", true);
        cardWidth = ConfigProperties.integer(properties, "cardWidth", 180, 100, 400);
        bgAlpha = ConfigProperties.integer(properties, "bgAlpha", 70, 0, 200);
        screenX = ConfigProperties.integer(properties, "screenX", 8, 0, 9_999);
        screenY = ConfigProperties.integer(properties, "screenY", 40, 0, 9_999);
        uiScale = parseFloat(properties.getProperty("uiScale"), 1.0f, 0.5f, 1.75f);
        keyCode = ConfigProperties.integer(properties, "keyCode", DEFAULT_KEY, -108, GLFW.GLFW_KEY_LAST);
    }

    private void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("overlayEnabled", Boolean.toString(overlayEnabled));
        properties.setProperty("showHidden", Boolean.toString(showHidden));
        properties.setProperty("showDistance", Boolean.toString(showDistance));
        properties.setProperty("reviveHoloB", Boolean.toString(reviveHoloB));
        properties.setProperty("cardWidth", Integer.toString(cardWidth));
        properties.setProperty("bgAlpha", Integer.toString(bgAlpha));
        properties.setProperty("screenX", Integer.toString(screenX));
        properties.setProperty("screenY", Integer.toString(screenY));
        properties.setProperty("uiScale", Float.toString(uiScale));
        properties.setProperty("keyCode", Integer.toString(keyCode));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("teammate-hp.properties"), properties,
                    "MICx TeammateHP configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save TeammateHP configuration", exception);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float parseFloat(String value, float fallback, float min, float max) {
        try {
            return clamp(Float.parseFloat(value), min, max);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
