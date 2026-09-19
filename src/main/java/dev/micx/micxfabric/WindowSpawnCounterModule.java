package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Enemy;

import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * 某某窗口刷怪量 HUD — 按窗统计。
 *
 * <p>窗位映射来自 {@link SpawnMarkerModule#PRESET_SPAWNS} 实测坐标，用户标定：
 * A=P1 B=P2 C=P5 D=P3 E=P4 F=ULT G=CL H=ALT I=CR J=BR K=BL ；L-O 四 UFO 口为 MID 不参与。
 * 仅统计 11 个地面窗（y=72），UFO 不计入 HUD（渲染在天空，落地 spawn 不归此 HUD）。
 *
 * <p>计数口径：每个实体首次加入世界（Mixin handleAddEntity RETURN 时世界已有该实体）
 * 即按 XZ 平面最近窗归档一次（出生点），不再追踪移动。仅 AA 且回合进行中才计数；
 * 对局重置/切世界时清零。非 Enemy/Wolf/Golem 的装饰实体（armor_stand 等）不计。
 *
 * <p>渲染：独立可拖动 HUD 块 {@code zombies.window_spawn}，默认右上，4 列网格
 * 展示 11 窗当前波归档数；切段由怪出生驱动（表时刻只作闸门，RTT/卡顿免疫），
 * 最后一波起不再清零，保留到下一回合开始。用 HudLayoutRegistry 托管位移与缩放。
 */
public final class WindowSpawnCounterModule implements Module {
    /**
     * 11 窗定义：id 展示名, x, z, y（y = 该窗所在地面层）。顺序 = 渲染行优先。
     *
     * <p>y 逐窗配置而非统一 72：974 局全量复核显示 BR/BL 两个角的地面在 <b>y=73.0</b>
     * （各 19,266 / 19,264 只，100%），其余 9 窗在 y=72.0。若统一按 72 判定，
     * 收紧容差时 BR/BL 会直接归零。
     */
    public static final class WindowDef {
        public final String id;  // HUD 上显示的窗名
        public final double x, z, y;
        WindowDef(String id, double x, double z, double y) { this.id = id; this.x = x; this.z = z; this.y = y; }
    }

    /** 与 SpawnMarkerModule.PRESET_SPAWNS 一一对应（去掉 4 个 UFO y=105），按用户标定更名 CL/CR/BL/BR。 */
    public static final List<WindowDef> WINDOWS = Collections.unmodifiableList(Arrays.asList(
            new WindowDef("P1",   6.0,  32.0, 72.0),  // A  另有第二刷怪点 (6.5,31.5,y=69) 1,488 只，故 Y_TOL 须 ≥3
            new WindowDef("P2", -22.0,  16.0, 72.0),  // B
            new WindowDef("P5",  22.0,  14.0, 72.0),  // C
            new WindowDef("P3", -22.0,  10.0, 72.0),  // D
            new WindowDef("P4", -10.0,  -6.0, 72.0),  // E  真刷怪点 (-10.5,-5.5,72.0)，974 局全量 49,568 只、100% y=72。
                                                      //    此前改到 (-14,-0.5) 属误判：该点 3×3 整框压在练习区上，
                                                      //    20,444 只里 20,440 只是练习区的狼，真怪只剩 4 只。
            new WindowDef("ULT", 28.0,  32.0, 72.0),  // F
            new WindowDef("CL", -28.0,  28.0, 72.0),  // G  原 RC
            new WindowDef("ALT", 18.0,  44.0, 72.0),  // H
            new WindowDef("CR", -12.0,  40.0, 72.0),  // I  原 LC/cc
            new WindowDef("BR",  22.0, -14.0, 73.0),  // J  原 perk  地面 y=73（实测 19,266 只 100%）
            new WindowDef("BL",  34.0,  -2.0, 73.0)   // K  原 bc_ent 地面 y=73（实测 19,264 只 100%）
    ));

    private static String fullName(String id) {
        return switch (id) {
            case "BL" -> "BC Left";
            case "BR" -> "BC Right";
            case "CR" -> "CC Right";
            case "CL" -> "CC Left";
            default -> id;
        };
    }
    public static String fullNameOf(String id) { return fullName(id); }
    public String lastTooWindowId() {
        if (tooInEntries.isEmpty()) return null;
        return tooInEntries.get(tooInEntries.size()-1).windowId;
    }
    private static final java.util.Set<String> LATE_WINDOWS = java.util.Set.of("ULT","ALT","P1","P5");

    private static final WindowSpawnCounterModule INSTANCE = new WindowSpawnCounterModule();

    private static int indexOfWindow(String id) {
        for (int i = 0; i < WINDOWS.size(); i++) if (WINDOWS.get(i).id.equals(id)) return i;
        return -1;
    }

    /** 最近窗归档：XZ 欧氏距离最小的窗胜出（地面层 y 可忽略）。 */
    public static String nearestWindow(double x, double z) {
        String best = WINDOWS.get(0).id;
        double bestD = Double.MAX_VALUE;
        for (WindowDef w : WINDOWS) {
            double dx = x - w.x, dz = z - w.z;
            double d2 = dx * dx + dz * dz;
            if (d2 < bestD) { bestD = d2; best = w.id; }
        }
        return best;
    }

    /** 最近窗下标，-1 表示异常。 */
    static int nearestWindowIndex(double x, double z) {
        int best = 0; double bestD = Double.MAX_VALUE;
        for (int i = 0; i < WINDOWS.size(); i++) {
            WindowDef w = WINDOWS.get(i);
            double dx = x - w.x, dz = z - w.z;
            double d2 = dx * dx + dz * dz;
            if (d2 < bestD) { bestD = d2; best = i; }
        }
        return best;
    }

    private boolean enabled;
    private boolean configLoaded;
    private final int[] counts = new int[WINDOWS.size()];
    private final int[] totalCounts = new int[WINDOWS.size()];
    private int totalSpawns;
    private int displayMode = 0;
    int hudDx = 0, hudDy = 0;
    float hudScale = 1.0f;
    private int currentRound = -1;
    private int currentWave = -1;
    // TOO 窗开关（放本模块）
    private boolean tooWindowAlert = true;
    private boolean tooWindowPc = true;
    private boolean tooWindowSound = true;
    // 出生坐标缓存（packet -> entity 第一帧）
    private final java.util.Map<Integer, Vec3> birthPosById = new java.util.LinkedHashMap<Integer, Vec3>() {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<Integer, Vec3> e) { return size() > 512; }
    };
    // 出生窗归档：entityId -> WINDOWS index。AA 局内无条件记录（Aimbot 窗优先依赖），
    // 不受本模块开关影响；几何口径与计数归档完全一致（windowIndexForBirth）。
    // 特殊值 BIRTH_WINDOW_MID：UFO 4 口（y≈105）出生的怪，供 SR P5+MID 模式识别。
    private final java.util.Map<Integer, Integer> birthWindowById = new java.util.LinkedHashMap<Integer, Integer>() {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<Integer, Integer> e) { return size() > 512; }
    };
    /** UFO（MID）出生的归档值：不是 WINDOWS index，{@link #birthWindowIdOf} 会转成 "MID"。 */
    public static final int BIRTH_WINDOW_MID = -2;
    // 本回合有 TOO 的窗
    private final boolean[] hasToo = new boolean[WINDOWS.size()];
    // TOO IN 列表：本回合内去重窗，TTL 3 波
    private static final class TooInEntry { String windowId; int birthRound; int birthWave; long birthMs; }
    private final List<TooInEntry> tooInEntries = new ArrayList<>();
    // 每只 TOO 的独立 /pc 序列
    private static final class TooSession {
        int entityId; String windowId; String windowFull; Vec3 birthPos;
        int birthRound; int birthWave; long birthMs;
        int step; long nextAtMs; int burstSent;
        boolean followPhase; int followSent; int followTotal;
        boolean r101Loop; boolean done;
    }
    private final List<TooSession> tooSessions = new ArrayList<>();
    private final java.util.Set<Integer> knownTooSessionIds = new java.util.HashSet<>();
    private static final class PendingToo { int entityId; Vec3 birthPos; long birthMs; int birthRound; }
    private final java.util.Map<Integer, PendingToo> pendingToos = new java.util.LinkedHashMap<>();

    private WindowSpawnCounterModule() {}

    public static WindowSpawnCounterModule instance() { return INSTANCE; }

    @Override public String id() { return "window_spawn_counter"; }
    @Override public boolean defaultEnabled() { return true; }
    @Override public boolean enabled() { return enabled; }
    @Override public void setEnabled(boolean v) {
        loadConfig();
        enabled = v;
        ModuleStateStore.put(id(), v);
    }

    public void setDisplayMode(int mode) { displayMode = mode == 1 ? 1 : 0; saveConfig(); }
    public int displayMode() { loadConfig(); return displayMode; }

    void setHudOffsets(int dx, int dy) { hudDx = dx; hudDy = dy; saveConfig(); }
    void setHudScale(float s) { hudScale = s; saveConfig(); }

    /**
     * P4 (-10,-6) 的练习靶：静止假怪，坐标几乎重合 P4。用出生坐标 + 尺寸/名字判定。
     *
     * <p>尺寸判定对 baby 豁免：小僵尸（含 TOO）的碰撞箱本就矮小（宽 0.3 / 高 0.975），
     * 按原写法会被误判成练习靶而丢弃，导致 P4 窗的 TOO 从不计数。
     */
    private static boolean isPracticeDummy(LivingEntity e, double bx, double bz) {
        double dx = bx - (-10.0), dz = bz - (-6.0);
        if (dx * dx + dz * dz > 16) return false; // 4格外不管

        // 狼（幽灵犬）是有效战斗单位，但成年狼碰撞箱高仅 0.85，会被下面的 h<1.2 误判成练习靶。
        // P4 真刷怪点 (-10.5,-5.5) 距本锚点仅 0.71 格，不先放行会把 P4 窗的狼全部丢弃
        // （974 局全量实测 2,584 只）。必须放在尺寸判定之前。
        if (e instanceof Wolf) return false;

        // 隐身盔甲架视为靶
        try { if (e.isInvisible()) return true; } catch (Throwable ignored) {}
        // 矮模型/极小碰撞箱视为靶（baby 除外，小僵尸本就矮小）
        try {
            if (!e.isBaby()) {
                float w = e.getBbWidth();
                float h = e.getBbHeight();
                if (w < 0.6f || h < 1.2f) return true;
            }
        } catch (Throwable ignored) {}
        try { String n = e.getName().getString(); if (n != null && (n.contains("Target") || n.contains("Practice") || n.contains("Dummy"))) return true; } catch (Throwable ignored) {}
        return false;
    }

    /** 出生归档诊断（-Dmicx.diag=true 开启）：定位漏计是几何判定还是上游未触发。 */
    private static void logBirth(LivingEntity e, double bx, double by, double bz, int idx) {
        WindowDef w = WINDOWS.get(nearestWindowIndex(bx, bz));
        String type;
        try { type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath(); } catch (Throwable t) { type = "?"; }
        MicxFabric.LOGGER.info("[micx-wspawn] birth id={} type={} baby={} pos=({},{},{}) nearest={} dist={} dy={} idx={}",
                e.getId(), type, e.isBaby(), one(bx), one(by), one(bz), w.id,
                one(Math.hypot(bx - w.x, bz - w.z)), one(Math.abs(by - w.y)), idx);
    }

    private static String one(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    /**
     * 窗口归档判定参数：出生点按 XZ 归到最近窗，且须落在该窗有效范围内。
     *
     * <p>有效刷怪 = 3×3 方块（中心 ±1.5 格，含边界），y = 该窗地面层 ±3.0（逐窗配置）。用户定版口径：
     * 「windowspawn 刷怪坐标 3*3 的范围可以当做有效刷怪」。
     *
     * <p><b>整数层闸门</b>：出生 y 必须落在精确整数层（容差 0.1）。练习区刷在
     * y=72.8 / 73.8 / 74.8（974 局共 183,754 只，配比恒定 鸡:狼:牛=2:1:1），
     * 其中 46,220 只是狼 —— 走 isCountable 白名单拦不住，只能靠 Y 层隔离。
     * 加上闸门后归档半径不再受「练习区到 P4 的 6.04 格」限制，扩窗也不会被反噬。
     */
    private static final double HALF = 1.5;
    private static final double Y_TOL = 3.0;
    /**
     * 整数层闸门容差。<b>必须取 0.1 而不是 0.5</b>：练习区在 72.8，距最近整数 73 仅 0.2，
     * 用 0.5 会被判成「接近整数」从而放行（实测该写法在归档半径 8.0 时污染 20,440 只）。
     */
    private static final double INT_Y_TOL = 0.1;

    /**
     * 出生驱动切段参数（RTT/服务器卡顿免疫）：表时刻只当闸门，真正切段在「闸门已过后的第一只怪」
     * 归档时执行——先清上一段，再把这只怪计入新段（同帧原子）。怪没来就永不清零，来多晚都不丢已计数。
     */
    private static final long WAVE_GATE_EARLY_MS = 1500L;
    /** 静默兜底：超过下一波 nominal 该毫秒数仍无任何怪出生（全 UFO 波等）则切段但不清零，残留并入新段。 */
    private static final long WAVE_FORCE_ADVANCE_MS = 6000L;

    public void recordBirthPos(int id, double x, double y, double z) {
        birthPosById.put(id, new Vec3(x, y, z));
    }

    /** entityId → 出生窗 index（AA 归档口径）；查不到返回 -1（含 UFO/窗外出生/未归档）。 */
    public int birthWindowOf(int entityId) {
        Integer idx = birthWindowById.get(entityId);
        return idx != null ? idx : -1;
    }

    /** entityId → 出生窗显示名（如 "P2"；UFO 口出生为 "MID"）；查不到返回 null。 */
    public String birthWindowIdOf(int entityId) {
        Integer val = birthWindowById.get(entityId);
        if (val == null) return null;
        if (val == BIRTH_WINDOW_MID) return "MID";
        return val >= 0 && val < WINDOWS.size() ? WINDOWS.get(val).id : null;
    }

    public void onEntitiesRemoved(int[] ids) {
        for (int id : ids) { birthPosById.remove(id); birthWindowById.remove(id); }
        // 清理已死亡的 session 的 birthPos 也在 tick 中处理
    }

    private static boolean isTooSignature(Zombie zombie) {
        if (!zombie.isBaby()) return false;
        ItemStack helmet = zombie.getItemBySlot(EquipmentSlot.HEAD);
        if (helmet.isEmpty()) return false;
        String hp = BuiltInRegistries.ITEM.getKey(helmet.getItem()).getPath().toLowerCase();
        if (!(hp.endsWith("_skull") || hp.endsWith("_head") || "skull".equals(hp))) return false;
        ItemStack held = zombie.getMainHandItem();
        boolean diamondSword = !held.isEmpty() && BuiltInRegistries.ITEM.getKey(held.getItem()).getPath().toLowerCase().contains("diamond_sword");
        ItemStack chest = zombie.getItemBySlot(EquipmentSlot.CHEST);
        DyedItemColor dyed = chest.get(DataComponents.DYED_COLOR);
        Integer color = dyed == null ? null : dyed.rgb();
        // 与 1.8.9 一致：tooStrictGreen=true 时允许绿色皮革兜底，否则仅晚同步的装备导致首帧漏判
        return ZombieThreatRules.isToo(true, true, diamondSword, color, true);
    }
    /** 窗口刷怪只计 Zombie（+Wolf 幽灵犬除外由 isPracticeDummy 已挡）；史莱姆/巨人/烈焰人/铁傀儡等不计。 */
    static boolean isCountable(LivingEntity e) {
        if (e == null) return false;
        if (e instanceof IronGolem) return false;
        if (e instanceof net.minecraft.world.entity.monster.cubemob.Slime) return false;
        if (e instanceof net.minecraft.world.entity.monster.Giant) return false;
        if (e instanceof net.minecraft.world.entity.monster.Blaze) return false;
        if (e instanceof net.minecraft.world.entity.monster.cubemob.MagmaCube) return false;
        if (e instanceof Wolf) return true;
        return e instanceof net.minecraft.world.entity.monster.zombie.Zombie;
    }

    /** 最近窗归档：XZ 最近窗且落在该窗 3×3 内（±1.5 含边界），y 须为整数层且在该窗地面 ±3 内；-1 表示窗外出生。 */
    static int windowIndexForBirth(double x, double y, double z) {
        // 整数层闸门：一刀切掉练习区（72.8 / 73.8 / 74.8），成本仅为世界各处的 8 只噪声
        if (Math.abs(y - Math.rint(y)) > INT_Y_TOL + 1e-6) return -1;
        int best = nearestWindowIndex(x, z);
        WindowDef w = WINDOWS.get(best);
        if (Math.abs(y - w.y) > Y_TOL + 1e-6) return -1;
        if (Math.abs(x - w.x) > HALF + 1e-6 || Math.abs(z - w.z) > HALF + 1e-6) return -1;
        return best;
    }

    /**
     * 出生归档驱动的切段：先清上一段，再把这只怪计入新段（同一调用内先后执行，不存在把怪自己清掉）。
     * currentWave 为 1 基波号，封顶总波数——进入最后一波后此方法永不再清，数量保留到下一回合开始。
     */
    private void flipWaveOnBirth(int round, long elapsedMs) {
        if (round != currentRound || currentWave < 1) return;
        int target = flipTargetOnBirth(currentWave, elapsedMs, ZombiesRoundData.waveTimes(round));
        if (target > currentWave) {
            Arrays.fill(counts, 0);
            Arrays.fill(hasToo, false);
            currentWave = target;
        }
    }

    /** 纯函数：该出生时刻应归属的波（1 基）。闸门 = 下一波 nominal − WAVE_GATE_EARLY_MS，封顶总波数。 */
    static int flipTargetOnBirth(int currentWave, long elapsedMs, int[] times) {
        int target = currentWave;
        while (target < times.length && elapsedMs >= times[target] * 1000L - WAVE_GATE_EARLY_MS) target++;
        return target;
    }

    /** 纯函数：静默兜底切段。闸门 = 下一波 nominal + WAVE_FORCE_ADVANCE_MS；不清零由调用方保证。 */
    static int forceTarget(int currentWave, long elapsedMs, int[] times) {
        int target = currentWave;
        while (target < times.length && elapsedMs >= times[target] * 1000L + WAVE_FORCE_ADVANCE_MS) target++;
        return target;
    }

    /** 由 Mixin handleAddEntity 调用：每个实体出生点归档一次。 */
    public void onEntitySpawn(Entity entity) {
        // 出生窗归档（无条件段）：AA 回合内任何生物落在窗 3×3 就记窗，供 Aimbot 窗优先
        // 查询；不按类型过滤（几何归档，Aimbot 只查自己的候选），练习靶照旧排除。
        if (entity instanceof LivingEntity le) {
            ZombiesTracker z0 = ZombiesTracker.instance();
            if (z0 != null && z0.isInAlienArcadium() && z0.round() > 0) {
                Vec3 bp0 = birthPosById.get(entity.getId());
                double bx0 = bp0 != null ? bp0.x : entity.getX();
                double by0 = bp0 != null ? bp0.y : entity.getY();
                double bz0 = bp0 != null ? bp0.z : entity.getZ();
                if (!isPracticeDummy(le, bx0, bz0)) {
                    int wIdx0 = windowIndexForBirth(bx0, by0, bz0);
                    if (wIdx0 >= 0) {
                        birthWindowById.put(entity.getId(), wIdx0);
                    } else if (Math.abs(by0 - 105.0) <= 3.0
                            && AimbotRules.isInsideMidCell(bx0, bz0)) {
                        // UFO 4 口出生（y≈105 + MID 单元格内）：归档为 MID。
                        // 地面怪 y=72 不可能命中，练习区/窗外地面怪同样排除。
                        birthWindowById.put(entity.getId(), BIRTH_WINDOW_MID);
                    }
                }
            }
        }
        if (!enabled) return;
        if (!(entity instanceof LivingEntity le)) return;
        if (!isCountable(le)) return;
        Vec3 bp = birthPosById.get(entity.getId());
        double bx = bp != null ? bp.x : entity.getX();
        double by = bp != null ? bp.y : entity.getY();
        double bz = bp != null ? bp.z : entity.getZ();
        if (isPracticeDummy(le, bx, bz)) return;
        ZombiesTracker z = ZombiesTracker.instance();
        if (z == null || !z.isInAlienArcadium()) return;
        if (z.round() <= 0) return;
        if (z.roundStartMs() > 0) {
            long elapsed = Math.max(0L, System.currentTimeMillis() - z.roundStartMs());
            flipWaveOnBirth(z.round(), elapsed);
        }
        int idx = windowIndexForBirth(bx, by, bz);
        if (Boolean.getBoolean("micx.diag")) logBirth(le, bx, by, bz, idx);
        // 窗外出生不归此 HUD；TOO 判定也只对在窗出生的僵尸做（handleTooSpawn 本就要求在窗内）
        if (idx < 0) return;
        counts[idx]++;
        totalCounts[idx]++;
        totalSpawns++;
        if (le instanceof Zombie zombie) {
            if (isTooSignature(zombie)) handleTooSpawn(zombie, bx, by, bz);
            else enrollPendingToo(zombie, bx, by, bz, z.round());
        }
    }

    /**
     * 出生即入复核池：handleAddEntity RETURN 时 baby 标记与装备（头颅/钻石剑/染色胸甲）尚未同步
     * （随后到的 SetEntityData/SetEquipment 才带），此刻任何签名判定都必 false，
     * 故不能按"已见 baby+skull"门控，须让所有在窗僵尸都进池，tick 内逐帧复核。
     */
    private void enrollPendingToo(Zombie zombie, double bx, double by, double bz, int round) {
        int eid = zombie.getId();
        if (pendingToos.containsKey(eid) || knownTooSessionIds.contains(eid)) return;
        PendingToo pt = new PendingToo();
        pt.entityId = eid;
        pt.birthPos = new Vec3(bx, by, bz);
        pt.birthMs = System.currentTimeMillis();
        pt.birthRound = round;
        pendingToos.put(eid, pt);
    }

    private void handleTooSpawn(Zombie zombie, double bx, double by, double bz) {
        int idx = windowIndexForBirth(bx, by, bz);
        if (idx < 0) return;
        String wid = WINDOWS.get(idx).id;
        String wfull = fullName(wid);
        int round = ZombiesTracker.instance().round();
        long now = System.currentTimeMillis();
        // TOO IN 的 3 波 TTL 与 currentWave 同刻度（1 基波号），切段后判定天然正确
        int wave = currentWave > 0 ? currentWave : 1;
        // 标记红字 T
        hasToo[idx] = true;
        // TOO IN 去重：同回合同窗只留一条，TTL 3 波在 tick 中清理
        boolean already = false;
        for (TooInEntry e : tooInEntries) if (e.windowId.equals(wid) && e.birthRound == round) { already = true; break; }
        if (!already) { TooInEntry e2 = new TooInEntry(); e2.windowId = wid; e2.birthRound = round; e2.birthWave = wave; e2.birthMs = now; tooInEntries.add(e2); }
        // 中央 TOO 生成警报补窗名（委托给 ZombiesAssist 的 tooSpawnUntil 由本模块触发）
        // 每只独立 /pc 序列
        if (!tooWindowAlert) return;
        int eid = zombie.getId();
        if (knownTooSessionIds.contains(eid)) return;
        knownTooSessionIds.add(eid);
        if (round >= 59 && !LATE_WINDOWS.contains(wid)) return; // 后期仅 4 窗发 /pc，但仍已标红/TOO IN
        // r59 仅一次不带距离
        if (round == 59) {
            TooSession s2 = new TooSession();
            s2.entityId = eid; s2.windowId = wid; s2.windowFull = wfull; s2.birthPos = new Vec3(bx, by, bz);
            s2.birthRound = round; s2.birthWave = wave; s2.birthMs = now;
            s2.step = 0; s2.nextAtMs = now; s2.burstSent = 0; s2.followPhase = false; s2.r101Loop = false; s2.done = false;
            s2.followTotal = 0; // r59 单次
            tooSessions.add(s2);
            return;
        }
        // r101-105 每1s 直到死亡
        if (round >= 101) {
            TooSession s2 = new TooSession();
            s2.entityId = eid; s2.windowId = wid; s2.windowFull = wfull; s2.birthPos = new Vec3(bx, by, bz);
            s2.birthRound = round; s2.birthWave = wave; s2.birthMs = now;
            s2.step = 0; s2.nextAtMs = now; s2.r101Loop = true; s2.done = false;
            tooSessions.add(s2);
            return;
        }
        // r<=58 或 r60-100 后期：burst 0.6s*3 + follow 1.5s * N
        TooSession s2 = new TooSession();
        s2.entityId = eid; s2.windowId = wid; s2.windowFull = wfull; s2.birthPos = new Vec3(bx, by, bz);
        s2.birthRound = round; s2.birthWave = wave; s2.birthMs = now;
        s2.step = 0; s2.nextAtMs = now; s2.burstSent = 0; s2.followPhase = false;
        s2.followTotal = round <= 58 ? 5 : 4;
        s2.r101Loop = false; s2.done = false;
        tooSessions.add(s2);
    }

    public void onRoundChanged(int round) {
        Arrays.fill(counts, 0);
        Arrays.fill(hasToo, false);
        tooInEntries.clear();
        tooSessions.clear();
        knownTooSessionIds.clear();
        pendingToos.clear();
        currentRound = round;
        currentWave = 1;
    }

    public void onSessionReset() {
        Arrays.fill(counts, 0);
        Arrays.fill(totalCounts, 0);
        Arrays.fill(hasToo, false);
        tooInEntries.clear();
        tooSessions.clear();
        knownTooSessionIds.clear();
        pendingToos.clear();
        birthPosById.clear();
        totalSpawns = 0;
        // 连 currentRound/currentWave 一起复位：重进同回合时 tick 的回合分支会重新初始化
        currentRound = -1;
        currentWave = -1;
    }

    @Override public void resetState() { onSessionReset(); }

    // HUD 位置诊断节流（3s 一条）
    private long lastHudDiagMs = 0L;

    private boolean hudDiagDue() {
        long now = System.currentTimeMillis();
        if (now - lastHudDiagMs < 3000L) return false;
        lastHudDiagMs = now;
        return true;
    }

    @Override public void tick(net.minecraft.client.Minecraft client) {
        if (!enabled) return;
        ZombiesTracker z = ZombiesTracker.instance();
        if (z == null || !z.isInAlienArcadium() || z.round() <= 0 || z.roundStartMs() <= 0) return;
        long now = System.currentTimeMillis();
        long elapsed = Math.max(0L, now - z.roundStartMs());
        int round = z.round();
        if (round != currentRound) {
            Arrays.fill(counts, 0); Arrays.fill(hasToo, false); tooInEntries.clear();
            tooSessions.clear(); knownTooSessionIds.clear();
            currentRound = round; currentWave = 1;
        } else {
            // 静默兜底：下一波 nominal +6s 仍无怪出生（全 UFO 波等）则切段但不清零，残留并入新段
            currentWave = forceTarget(currentWave, elapsed, ZombiesRoundData.waveTimes(round));
        }
        // pending TOO 复核：出生时数据/装备未同步，每 tick 重判，3s 内转正
        if (!pendingToos.isEmpty() && client != null && client.level != null) {
            long nowMs = now;
            java.util.Iterator<java.util.Map.Entry<Integer, PendingToo>> pit = pendingToos.entrySet().iterator();
            while (pit.hasNext()) {
                var ent = pit.next();
                PendingToo pt = ent.getValue();
                if (nowMs - pt.birthMs > 3000L) { pit.remove(); continue; }
                if (pt.birthRound != round) { pit.remove(); continue; }
                var e = client.level.getEntity(pt.entityId);
                if (e == null || e.isRemoved() || !e.isAlive()) { pit.remove(); continue; }
                if (!(e instanceof Zombie z2)) continue;
                if (isTooSignature(z2)) {
                    handleTooSpawn(z2, pt.birthPos.x, pt.birthPos.y, pt.birthPos.z);
                    pit.remove();
                }
            }
        }
        // TOO IN: 仅显示最近 3 波内的条目
        if (!tooInEntries.isEmpty()) {
            tooInEntries.removeIf(e -> e.birthRound != round || (currentWave - e.birthWave) >= 3);
        }
        // 驱动 /pc 序列
        if (!tooSessions.isEmpty() && client != null && client.player != null && client.level != null) {
            java.util.Iterator<TooSession> it = tooSessions.iterator();
            while (it.hasNext()) {
                TooSession sess = it.next();
                if (sess.done) { it.remove(); continue; }
                if (sess.birthRound != round) { it.remove(); continue; }
                // 死亡/移除则停止 r101 循环，其余序列不受死亡影响（已生成即需播完）
                boolean alive = true;
                try { var ent = client.level.getEntity(sess.entityId); alive = ent != null && !ent.isRemoved() && ent.isAlive(); } catch (Throwable ignored) {}
                if (sess.r101Loop && !alive) { it.remove(); continue; }
                if (now < sess.nextAtMs) continue;
                // 计算最近玩家与距离
                String nearestName = null; double bestDist = Double.MAX_VALUE;
                Vec3 tooPos = null;
                try { var ent = client.level.getEntity(sess.entityId); if (ent != null) tooPos = ent.position(); } catch (Throwable ignored) {}
                if (tooPos == null) tooPos = sess.birthPos;
                double selfDist = Double.MAX_VALUE;
                try {
                    for (Player pl : client.level.players()) {
                        if (pl == null) continue;
                        double d = pl.position().distanceTo(tooPos);
                        if (d < bestDist) { bestDist = d; nearestName = pl.getName().getString(); }
                        if (pl == client.player) selfDist = d;
                    }
                } catch (Throwable ignored) {}
                boolean selfNearest = false;
                try { selfNearest = nearestName != null && client.player != null && nearestName.equals(client.player.getName().getString()); } catch (Throwable ignored) {}
                // 若最近距离未算出，回退到出生点到自己的距离
                if (bestDist == Double.MAX_VALUE) bestDist = selfDist != Double.MAX_VALUE ? selfDist : 0;
                int distInt = (int) Math.round(bestDist);
                // r59 仅一次不带距离
                if (sess.birthRound == 59 && sess.step == 0) {
                    String pcMsg;
                    if (selfNearest) pcMsg = "pc TOO 生成于 " + sess.windowFull + "! 距离 我 最近!";
                    else pcMsg = "pc TOO 生成于 " + sess.windowFull + "! 距离" + (nearestName != null ? nearestName : "?") + "最近!";
                    sendPcAndLocal(client, pcMsg, sess, distInt, selfNearest, nearestName, false);
                    sess.done = true;
                    it.remove();
                    continue;
                }
                if (sess.r101Loop) {
                    // 每1s 一条直到死亡/结束
                    String pcMsg;
                    if (selfNearest) pcMsg = "pc TOO 生成于 " + sess.windowFull + "! 距离 我 最近! " + distInt + "m";
                    else pcMsg = "pc TOO 生成于 " + sess.windowFull + "! 距离" + (nearestName != null ? nearestName : "?") + "最近! " + distInt + "m";
                    boolean withWindowName = sess.step < 3;
                    sendPcAndLocal(client, pcMsg, sess, distInt, selfNearest, nearestName, withWindowName);
                    sess.step++;
                    sess.nextAtMs = now + 1000L;
                    continue;
                }
                // 普通 burst+follow
                if (!sess.followPhase) {
                    String pcMsg;
                    if (selfNearest) pcMsg = "pc TOO 生成于 " + sess.windowFull + "! 距离 我 最近! " + distInt + "m";
                    else pcMsg = "pc TOO 生成于 " + sess.windowFull + "! 距离" + (nearestName != null ? nearestName : "?") + "最近! " + distInt + "m";
                    boolean withWindowName = sess.step < 3;
                    sendPcAndLocal(client, pcMsg, sess, distInt, selfNearest, nearestName, withWindowName);
                    sess.burstSent++; sess.step++;
                    if (sess.burstSent >= 3) { sess.followPhase = true; sess.nextAtMs = now + 1500L; }
                    else sess.nextAtMs = now + 600L;
                    if (sess.burstSent >= 3 && sess.followTotal == 0) { sess.done = true; it.remove(); }
                    continue;
                } else {
                    // follow 阶段每1.5s
                    String pcMsg = "pc TOO 距离" + (selfNearest ? "我" : (nearestName != null ? nearestName : "?")) + " " + distInt + "m";
                    boolean withWindowName = sess.step < 3;
                    sendPcAndLocal(client, pcMsg, sess, distInt, selfNearest, nearestName, withWindowName);
                    sess.followSent++; sess.step++;
                    if (sess.followSent >= sess.followTotal) { sess.done = true; it.remove(); }
                    else sess.nextAtMs = now + 1500L;
                }
            }
        }
    }

    private void sendPcAndLocal(Minecraft client, String pcCommand, TooSession sess, int distInt, boolean selfNearest, String nearestName, boolean withWindowName) {
        try {
            if (tooWindowPc && client.player != null && client.player.connection != null) {
                client.player.connection.sendCommand(pcCommand.startsWith("pc ") ? pcCommand : "pc " + pcCommand);
            }
        } catch (Throwable ignored) {}
        try {
            if (client.player != null) {
                String local;
                if (sess != null && sess.birthRound == 59) {
                    if (selfNearest) local = "TOO 生成于 " + sess.windowFull + "! 距离 我 最近!";
                    else local = "TOO 生成于 " + sess.windowFull + "! 距离" + (nearestName != null ? nearestName : "?") + "最近!";
                } else if (withWindowName && sess != null) {
                    if (selfNearest) local = "TOO 生成于 " + sess.windowFull + "! 距离 我 最近! " + distInt + "m";
                    else local = "TOO 生成于 " + sess.windowFull + "! 距离" + (nearestName != null ? nearestName : "?") + "最近! " + distInt + "m";
                } else {
                    if (selfNearest) local = "TOO 距离我 " + distInt + "m";
                    else local = "TOO 距离" + (nearestName != null ? nearestName : "?") + " " + distInt + "m";
                }
                client.player.sendSystemMessage(Component.literal(local).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
                if (tooWindowSound) {
                    try { client.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f)); } catch (Throwable ignored) {
                        try { client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.0f); } catch (Throwable ignored2) {}
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    /** 本回合快照（按 WINDOWS 顺序）。 */
    public int[] snapshot() { return Arrays.copyOf(counts, counts.length); }
    public int[] totalSnapshot() { return Arrays.copyOf(totalCounts, totalCounts.length); }
    public int totalSpawns() { return totalSpawns; }

    // ——— HUD ——— 透明无底无标题，4 列，0 个不显示，倒二波黄/末波橙；有 TOO 的窗全红 T
    public void drawHud(GuiGraphicsExtractor g) {
        if (!enabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return;
        ZombiesTracker z = ZombiesTracker.instance();
        if (z == null || !z.isInAlienArcadium()) return;
        int[] src = counts;
        int waveColor = 0xFFE6EDE9;
        try {
            int[] wtimes = ZombiesRoundData.waveTimes(z.round());
            int total = wtimes.length;
            if (total > 0 && currentWave >= 1) {
                // currentWave 为 1 基波号：最后一波（== total）橙，倒数第二波黄
                if (currentWave >= total) waveColor = 0xFFFF9800;
                else if (currentWave == total - 1) waveColor = 0xFFFFC107;
            }
        } catch (Throwable ignored) {}

        String[][] cols = { {"P1","P2","P3","P4","P5"}, {"CR","CL"}, {"ULT","ALT"}, {"BR","BL"} };
        // 收集每列的行：每行带颜色
        class Cell { String text; int color; }
        List<List<Cell>> colCells = new ArrayList<>();
        int maxRows = 0;
        for (String[] col : cols) {
            List<Cell> cells = new ArrayList<>();
            for (String id : col) {
                int idx = indexOfWindow(id);
                if (idx < 0) continue;
                int v = src[idx];
                boolean isToo = hasToo[idx];
                if (v == 0 && !isToo) continue;
                Cell c = new Cell();
                if (isToo) { c.text = String.format(Locale.ROOT, "%-3s  T", id); c.color = 0xFFFF3B30; }
                else { c.text = String.format(Locale.ROOT, "%-3s %2d", id, v); c.color = waveColor; }
                cells.add(c);
            }
            colCells.add(cells);
            maxRows = Math.max(maxRows, cells.size());
        }
        // TOO IN 行：去重窗
        List<String> tooInLines = new ArrayList<>();
        for (TooInEntry e : tooInEntries) tooInLines.add("TOO IN " + e.windowId);
        if (maxRows == 0 && tooInLines.isEmpty()) return;

        float sx = HudLayoutRegistry.scaleX("zombies.window_spawn", hudScale);
        float sy = HudLayoutRegistry.scaleY("zombies.window_spawn", hudScale);
        int sw = g.guiWidth(), sh = g.guiHeight();
        HudLayoutBlock block = null;
        for (HudLayoutBlock b : HudLayoutRegistry.blocks()) if ("zombies.window_spawn".equals(b.id())) { block = b; break; }
        int baseX, baseY;
        if (block != null) { baseX = block.x(sw, sh); baseY = block.y(sw, sh); }
        else { baseX = sw - 220; baseY = 96; }
        if (Boolean.getBoolean("micx.diag") && hudDiagDue()) {
            MicxFabric.LOGGER.info("[micx-wspawn] hud base=({},{}) hudDx={} hudDy={} scale={} screen=({},{}) render=({},{}) found={}",
                    baseX, baseY, hudDx, hudDy, one(hudScale), sw, sh,
                    block == null ? -1 : block.renderWidth(), block == null ? -1 : block.renderHeight(), block != null);
        }
        g.pose().pushMatrix();
        g.pose().translate(baseX, baseY);
        g.pose().scale(sx, sy);
        try {
            int y = 0;
            // TOO IN 置顶（红）
            for (String line : tooInLines) {
                g.text(mc.font, Component.literal(line), 0, y, 0xFFFF3B30, true);
                y += 11;
            }
            if (!tooInLines.isEmpty() && maxRows > 0) y += 2;
            // 列距紧凑：每列宽 = 该列最宽文本 + 一个空格宽
            int[] colX = new int[colCells.size()];
            int cursor = 0;
            for (int c = 0; c < colCells.size(); c++) {
                colX[c] = cursor;
                int w = 0;
                for (Cell cell : colCells.get(c)) w = Math.max(w, mc.font.width(Component.literal(cell.text)));
                cursor += w + 6;
            }
            int rowY = y;
            int maxR = maxRows;
            for (int r = 0; r < maxR; r++) {
                for (int c = 0; c < colCells.size(); c++) {
                    List<Cell> cells = colCells.get(c);
                    if (r >= cells.size()) continue;
                    Cell cell = cells.get(r);
                    g.text(mc.font, Component.literal(cell.text), colX[c], rowY, cell.color, true);
                }
                rowY += 11;
            }
        } finally { g.pose().popMatrix(); }
    }

    // 面板用
    public boolean tooWindowAlert() { loadConfig(); return tooWindowAlert; }
    public void setTooWindowAlert(boolean v) { loadConfig(); tooWindowAlert = v; saveConfig(); }
    public boolean tooWindowPc() { loadConfig(); return tooWindowPc; }
    public void setTooWindowPc(boolean v) { loadConfig(); tooWindowPc = v; saveConfig(); }
    public boolean tooWindowSound() { loadConfig(); return tooWindowSound; }
    public void setTooWindowSound(boolean v) { loadConfig(); tooWindowSound = v; saveConfig(); }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path cur = FabricRuntime.configPath().resolve("window-spawn-counter.properties");
        Path leg = FabricRuntime.configPath().getParent().resolve("MICxToolkit_WindowSpawnCounter.cfg");
        Properties p = ConfigProperties.load(cur, leg);
        hudDx = ConfigProperties.integer(p, "hudDx", 0, -5000, 5000);
        hudDy = ConfigProperties.integer(p, "hudDy", 0, -5000, 5000);
        { String v = p.getProperty("hudScale"); if (v != null) try { float f = Float.parseFloat(v.trim()); if (Float.isFinite(f)) hudScale = Math.max(0.5f, Math.min(2.0f, f)); } catch (Exception ignored) {} }
        displayMode = ConfigProperties.integer(p, "displayMode", 0, 0, 1);
        tooWindowAlert = ConfigProperties.bool(p, "tooWindowAlert", true);
        tooWindowPc = ConfigProperties.bool(p, "tooWindowPc", true);
        tooWindowSound = ConfigProperties.bool(p, "tooWindowSound", true);
    }

    private void saveConfig() {
        Properties p = new Properties();
        p.setProperty("hudDx", Integer.toString(hudDx));
        p.setProperty("hudDy", Integer.toString(hudDy));
        p.setProperty("hudScale", Float.toString(hudScale));
        p.setProperty("displayMode", Integer.toString(displayMode));
        p.setProperty("tooWindowAlert", Boolean.toString(tooWindowAlert));
        p.setProperty("tooWindowPc", Boolean.toString(tooWindowPc));
        p.setProperty("tooWindowSound", Boolean.toString(tooWindowSound));
        try { AtomicProperties.store(FabricRuntime.configPath().resolve("window-spawn-counter.properties"), p, "MICx WindowSpawnCounter"); }
        catch (IOException e) { MicxFabric.LOGGER.warn("Unable to save WindowSpawnCounter configuration", e); }
    }
}
