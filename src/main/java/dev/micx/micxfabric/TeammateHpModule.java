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
 * <p>与 Forge 的差异（数据源缺失，暂缓项）：
 * <ul>
 *   <li>DOWN 卡无出血倒计时 —— Fabric ZombiesTracker 暂无 downElapsedMs；</li>
 *   <li>无 fr 二救冷却显示与 ReviveHolo 救援进度 —— 无对应信源。</li>
 * </ul></p>
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
                    drawDownGhost(graphics, name, x, y);
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

    /** 倒地且实体被服务器换成尸体 NPC：紫条占位卡。（出血倒计时暂缓：无 downElapsedMs 信源） */
    private void drawDownGhost(GuiGraphicsExtractor graphics, String name, int x, int y) {
        Minecraft client = Minecraft.getInstance();
        int right = drawCardBase(graphics, client, name, x, y, 0xFFB05CFF, 0xFFD9A8FF);
        graphics.text(client.font, "DOWN", right - client.font.width("DOWN"), y + 5, 0xFFB05CFF, true);
        int barY = y + CARD_HEIGHT - BAR_H - 5;
        graphics.fill(contentLeft(x), barY, right, barY + BAR_H, 0x99000000);
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
        boolean isBlocking = player.isBlocking();
        boolean isSneaking = player.isCrouching();
        boolean isFiring = tracker.isFiring(name, now);
        // 近战剑语义化：拿刀不格挡 = 木剑；格挡中 = 钻石剑（isBlocking 服务端同步）
        ItemStack iconStack = held;
        if (held != null && !held.isEmpty() && held.getItem().builtInRegistryHolder().is(ItemTags.SWORDS)) {
            iconStack = new ItemStack(isBlocking ? Items.DIAMOND_SWORD : Items.WOODEN_SWORD);
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

        // HP number — 右对齐；倒地显示 DOWN 占位（Hypixel 会重置假满血，且无出血倒计时信源）
        String hpText;
        int hpColor;
        if (isDown) {
            hpText = "DOWN";
            hpColor = 0xFFB05CFF;
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

        // Distance — HP 左侧灰字
        if (showDistance && !isSelf) {
            String distText = String.format("+%.1fm", client.player.distanceTo(player));
            int distW = client.font.width(distText);
            int distX = statusRightX - hpW - 6 - distW;
            if (distX >= contentX) {
                graphics.text(client.font, distText, distX, textY, 0xFF999999, false);
            }
        }

        // Bottom bar — 绝对血量刻度：底槽长度 = 自己血池/全队最大血池
        int troughW = Math.max(10, (int) (contentW * (max / scaleMax)));
        graphics.fill(contentX, barY, contentX + troughW, barY + BAR_H, 0x99000000);
        int filled = (int) (troughW * Math.min(ratio, 1.0f));
        if (filled > 0) {
            graphics.fill(contentX, barY, contentX + filled, barY + BAR_H, hpBarColor(hp));
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

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path current = FabricRuntime.configPath().resolve("teammate-hp.properties");
        Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_TeammateHP.cfg");
        Properties properties = ConfigProperties.load(current, legacy);
        overlayEnabled = ConfigProperties.bool(properties, "overlayEnabled", true);
        showHidden = ConfigProperties.bool(properties, "showHidden", false);
        showDistance = ConfigProperties.bool(properties, "showDistance", true);
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
