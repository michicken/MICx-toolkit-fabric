package dev.micx.micxfabric;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import dev.micx.micxfabric.mixin.RenderPipelinesAccess;
import dev.micx.micxfabric.mixin.RenderTypeAccess;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AimLead —— 高延迟预瞄补偿（纯渲染，不改任何发包）。v0.2.11 全量对齐 Forge 1.8.9：
 *
 * <ul>
 *   <li>采样：客户端渲染位置（服务器广播 + 本地模拟合成，覆盖投放/飘落怪），
 *       同坐标短间隔去重、定时补采样以保留静止目标；packet mixin 路径只保留 L0 影子框
 *       （服务器最后报的坐标）用途。</li>
 *   <li>测速：相邻样本对差分（≤8 对/1500ms 窗）→ rejectVelocityPair 剔尖峰 →
 *       水平分量中位数 + verticalVelocity 短窗（3 对）中位数；lv* 兜底 ≤2500ms。</li>
 *   <li>转向检测：新旧半窗 cos&lt;0.766 或速率比 &lt;0.4/&gt;2.5 → lowConf + 短窗响应。</li>
 *   <li>急停/停更：STOP_WINDOW 160ms 净位移 + 中位速度 → justStopped；
 *       SERVER_STALE 250ms + 慢速豁免 0.3 m/s → serverStale；shouldHoldBack 收缩。</li>
 *   <li>碰撞钳制（X/Z sweep，120ms 轨迹缓存 + 脚底换列立即失效，贴脚下板）+ 地形跟随（落点面高夹取，300ms 缓存）。</li>
 *   <li>帧间平滑 rx/ry/rz：移动 0.78 / 收缩 0.92。</li>
 *   <li>渲染：L2 命中=绿(20% 填充)、正常=黄(11%)、lowConf=灰(5%)、L0 影子=青；
 *       绿点 HUD 以 fireNowAtMs 粘滞 120ms。</li>
 *   <li>ping：game-RTT（tab 补全往返）→ tab → SLP（服务器列表协议，daemon 5s）
 *       → manual；连接切换清样本。</li>
 * </ul>
 */
public final class AimLeadModule implements Module {
    private static final AimLeadModule INSTANCE = new AimLeadModule();
    private static final int SAMPLE_CAP = 12;
    private static final long SAMPLE_TTL_MS = 2_000L;
    /** 静止目标也要周期性补一个时间样本，否则速度轨迹会永远只有 1 点并过期。 */
    private static final long STATIONARY_SAMPLE_INTERVAL_MS = 100L;
    private static final long STALE_DROP_MS = 3_500L;
    private static final double MAX_SPEED = 15.0;        // 水平限幅（垂直不限）
    private static final double RAY_RANGE = 70.0;        // L2 射线长度
    private static final long FIRE_DOT_STICKY_MS = 120L;
    private static final RenderType LINE_TYPE = EspRenderTypes.espLines();
    private static final RenderType FILL_TYPE = createFillType();

    private final AimLeadConfig config = new AimLeadConfig();
    private final Map<Integer, Track> tracks = new ConcurrentHashMap<>();
    private volatile Object activeLevel;
    private volatile Object latencyConnection;
    private volatile int gameRtt = -1;
    private volatile int gameJitter;
    private volatile long gameAt;
    private final int[] gameRecent = new int[5];
    private int gameRecentN;
    private volatile long rttRequestAt;
    private volatile int rttRequestId;
    private volatile int nextRttId;
    private long lastRttRequestMs;
    private boolean enabled;

    /* ---- SLP ping（daemon 线程，5s 周期） ---- */
    private volatile boolean slpRun = false;
    private Thread slpThread;
    private final int[] slpRecent = new int[3];
    private int slpRecentN;
    private volatile int slpPing = 0;
    private volatile long slpAt = 0;

    /* ---- 渲染帧复用（客户端单线程） ---- */
    private final Set<Integer> lastDrawnIds = new HashSet<>();
    private volatile long fireNowAtMs = 0L;
    private final double[] pvx = new double[8], pvy = new double[8], pvz = new double[8];
    private final double[] medScratch = new double[8];
    /** 转向角速度中位数暂存（TURN_WINDOW_PAIRS 对）。 */
    private final double[] turnScratch = new double[AimLeadRoundRules.TURN_WINDOW_PAIRS];

    private AimLeadModule() {
        LevelRenderEvents.COLLECT_SUBMITS.register(this::collectSubmits);
    }

    public static AimLeadModule instance() {
        return INSTANCE;
    }

    /* ==================== packet 入口：仅维护 L0 影子坐标（服务器最后广播位置） ==================== */

    public static void recordSpawn(int id, Vec3 position) {
        INSTANCE.shadow(id, position);
    }

    public static void recordRelative(Entity entity, Vec3 position) {
        if (entity != null) INSTANCE.shadow(entity.getId(), position);
    }

    public static void recordAbsolute(int id, Vec3 position) {
        INSTANCE.shadow(id, position);
    }

    public static void recordTeleport(int id, Vec3 currentPosition, Entity entity,
                                      PositionMoveRotation change, java.util.Set<Relative> relatives) {
        if (entity == null || change == null || relatives == null) return;
        Track track = INSTANCE.tracks.get(id);
        Vec3 base = track == null ? currentPosition : track.latest();
        if (base == null) return;
        PositionMoveRotation previous = new PositionMoveRotation(base, entity.getKnownMovement(),
                entity.getYRot(), entity.getXRot());
        PositionMoveRotation absolute = PositionMoveRotation.calculateAbsolute(previous, change, relatives);
        INSTANCE.shadow(id, absolute.position());
    }

    /** L0 影子：服务器最后报的绝对坐标；不进测速样本（测速用 tick 的渲染位置）。 */
    private void shadow(int id, Vec3 position) {
        if (!enabled || position == null) return;
        Track track = tracks.get(id);
        if (track == null) return;   // 只给已有轨迹的目标记录影子（不凭空建轨迹）
        track.setShadow(position);
    }

    public static void remove(int id) {
        INSTANCE.tracks.remove(id);
    }

    public static void clearTracks() {
        INSTANCE.tracks.clear();
        INSTANCE.lastDrawnIds.clear();
        INSTANCE.fireNowAtMs = 0L;
        synchronized (INSTANCE) {
            INSTANCE.gameRtt = -1;
            INSTANCE.gameJitter = 0;
            INSTANCE.gameAt = 0L;
            java.util.Arrays.fill(INSTANCE.gameRecent, 0);
            INSTANCE.gameRecentN = 0;
        }
        INSTANCE.rttRequestAt = 0L;
        INSTANCE.lastRttRequestMs = 0L;
        INSTANCE.activeLevel = null;
        INSTANCE.latencyConnection = null;
        INSTANCE.clearSlp();
    }

    @Override
    public void resetState() {
        clearTracks();
    }

    public static void recordRttResponse(int id) {
        if (!INSTANCE.enabled) return;
        if (INSTANCE.rttRequestAt != 0L && INSTANCE.rttRequestId == id) {
            long elapsed = Math.max(1L, System.nanoTime() / 1_000_000L - INSTANCE.rttRequestAt);
            INSTANCE.rttRequestAt = 0L;
            if (elapsed > 0L && elapsed < 5_000L) INSTANCE.pushGame((int) elapsed);
        }
    }

    @Override
    public String id() {
        return "aim_lead";
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
        if (enabled) startSlp();
        else {
            stopSlp();
            clearTracks();
        }
        ModuleStateStore.put(id(), enabled);
    }

    /* ==================== tick：渲染位置采样 + game-RTT ==================== */

    @Override
    public void tick(Minecraft client) {
        loadConfig();
        if (!enabled || client == null || client.level == null || client.player == null) {
            if (activeLevel != null && client != null && client.level != activeLevel) clearTracks();
            activeLevel = client == null ? null : client.level;
            return;
        }
        if (activeLevel != client.level) {
            clearTracks();
            activeLevel = client.level;
        }
        long now = System.currentTimeMillis();
        long nowMono = System.nanoTime() / 1_000_000L;

        // game-RTT 常开测量（每 10s 一次 ≈ 聊天栏按一下 Tab）
        Object connection = client.getConnection();
        if (latencyConnection != connection) {
            latencyConnection = connection;
            clearLatencySamples();
            lastRttRequestMs = 0L;
        }
        if (rttRequestAt != 0L && nowMono - rttRequestAt > 3_000L) {
            rttRequestAt = 0L;
        }
        if (config.gameRtt && config.autoPing && rttRequestAt == 0L && connection != null
                && now - lastRttRequestMs > 10_000L) {
            requestRtt(client);
        }

        boolean broadAimbotTracking = AimbotModule.instance().needsAimLeadTracking();
        if (config.zombiesOnly && !ZombiesTracker.instance().isInZombies() && !broadAimbotTracking) {
            if (!tracks.isEmpty()) tracks.clear();
            return;
        }

        // 渲染位置采样（对齐 Forge 2.26.9 posX 决策）
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !isTarget(living)) continue;
            if (living.isDeadOrDying() || living.getHealth() <= 0f) continue;
            if (living.tickCount < 6) continue;   // 刚刷出，位置未稳定

            Track track = tracks.computeIfAbsent(entity.getId(), ignored -> {
                Track t = new Track();
                // 新生怪加速：用客户端 motion 预填速度，首个样本即可低置信预测
                Vec3 delta = living.getDeltaMovement();
                if (delta.lengthSqr() > 0.0025) {
                    t.lvx = delta.x;
                    t.lvy = delta.y;
                    t.lvz = delta.z;
                }
                t.lvAt = now;
                return t;
            });
            track.lastSeenMs = now;
            Vec3 pos = living.position();
            int n = track.size();
            if (n > 0) {
                int i0 = track.idx(0);
                if (track.xs[i0] == pos.x && track.ys[i0] == pos.y && track.zs[i0] == pos.z
                        && now - track.ts[i0] < STATIONARY_SAMPLE_INTERVAL_MS) {
                    continue;   // 未更新不重写；按时间间隔补样本，静止目标不会失去轨迹
                }
            }
            track.add(now, pos.x, pos.y, pos.z);
            // 提前量误差回检（渲染帧写预测、tick 里核对，避免渲染路径重复计算）
            trackLeadError(living, track, now, tauMs());
        }
        // 2s 未见的轨迹清理
        tracks.entrySet().removeIf(entry ->
                entry.getValue().size() == 0 || now - entry.getValue().lastSeenMs > SAMPLE_TTL_MS);
    }

    private void requestRtt(Minecraft client) {
        rttRequestId = ++nextRttId;
        rttRequestAt = System.nanoTime() / 1_000_000L;
        lastRttRequestMs = System.currentTimeMillis();
        client.getConnection().send(new net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket(
                rttRequestId, "/"));
    }

    /* ==================== HUD：准星绿点（120ms 粘滞） ==================== */

    public void drawHud(GuiGraphicsExtractor graphics) {
        if (!enabled || !config.renderGhost || !config.fireDot) return;
        if (System.currentTimeMillis() - fireNowAtMs > FIRE_DOT_STICKY_MS) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) return;
        int x = graphics.guiWidth() / 2 + config.markerOffsetX;
        int y = graphics.guiHeight() / 2 + config.markerOffsetY;
        float sx = HudLayoutRegistry.scaleX("aim_lead_marker", config.markerScaleX);
        float sy = HudLayoutRegistry.scaleY("aim_lead_marker", config.markerScaleY);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(sx, sy);
        try {
            graphics.fill(-2, 0, 3, 2, 0xFF00E676);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    /* ==================== 世界渲染：候选选择 + 三色幽灵框 ==================== */

    private void collectSubmits(LevelRenderContext context) {
        try {
            if (!enabled || !config.renderGhost) return;
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.level == null || client.player == null) return;
            if (config.zombiesOnly && !ZombiesTracker.instance().isInZombies()) return;

            List<Ghost> ghosts = buildGhosts(client);
            if (ghosts.isEmpty()) return;

            SubmitNodeCollector collector = context.submitNodeCollector();
            PoseStack pose = context.poseStack();
            Vec3 camera = client.gameRenderer.mainCamera().position();
            for (Ghost ghost : ghosts) {
                try {
                    Entity entity = ghost.entity();
                    double w = entity.getBbWidth() * 0.5;
                    double h = entity.getBbHeight();
                    double px = ghost.px() - camera.x, py = ghost.py() - camera.y, pz = ghost.pz() - camera.z;

                    // L0 影子框（青，调试）
                    if (config.serverShadow) {
                        Track track = tracks.get(entity.getId());
                        if (track != null) {
                            submitWireBox(collector, pose, track.lsx - camera.x, track.lsy - camera.y,
                                    track.lsz - camera.z, w, h, 0.59f, 0.85f, 0.95f, 0.35f);
                        }
                    }

                    // L1 幽灵框：命中=绿，正常=黄，低置信=灰（+半透明填充提高杂乱背景辨识度）
                    float cr, cg, cb, la, fa;
                    if (ghost.hit()) {
                        cr = 0.15f; cg = 0.95f; cb = 0.30f; la = 0.95f; fa = 0.20f;
                    } else if (ghost.lowConf()) {
                        cr = 0.60f; cg = 0.60f; cb = 0.60f; la = 0.45f; fa = 0.05f;
                    } else {
                        cr = 1.00f; cg = 0.85f; cb = 0.15f; la = 0.85f; fa = 0.11f;
                    }
                    submitFillBox(collector, pose, px, py, pz, w, h, cr, cg, cb, fa);
                    submitWireBox(collector, pose, px, py, pz, w, h, cr, cg, cb, la);

                    // 本体 → 幽灵连线（识别归属）
                    if (config.drawLink) {
                        double ex = entity.getX() - camera.x;
                        double ey = entity.getY() + h * 0.5 - camera.y;
                        double ez = entity.getZ() - camera.z;
                        submitLine(collector, pose, ex, ey, ez, px, py + h * 0.5, pz, 1.0f, 1.0f, 1.0f, 0.3f);
                    }
                } catch (Throwable t) {
                    MicxFabric.LOGGER.warn("AimLead ghost skipped: {}", t.toString());
                }
            }
        } catch (Throwable t) {
            MicxFabric.LOGGER.warn("AimLead collectSubmits skipped: {}", t.toString());
        }
    }

    /** 阶段①便宜角度粗排（粘滞）→ 阶段②昂贵 computeLeadPoint + L2 射线 → 精确角度终排。 */
    private List<Ghost> buildGhosts(Minecraft client) {
        List<Ghost> result = new ArrayList<>();
        long now = System.currentTimeMillis();
        double tauS = tauMs() / 1000.0;
        Vec3 eye = client.player.getEyePosition(1.0f);
        Vec3 look = client.player.getViewVector(1.0f);
        double minD2 = (double) config.minDist * config.minDist;

        List<Cand> cands = new ArrayList<>();
        for (Map.Entry<Integer, Track> entry : tracks.entrySet()) {
            Track track = entry.getValue();
            if (track.size() == 0) continue;
            Entity entity = client.level.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity living) || !isTarget(living)) continue;
            if (living.isDeadOrDying() || living.getHealth() <= 0f) continue;
            double dx = living.getX() - client.player.getX();
            double dy = living.getY() - client.player.getY();
            double dz = living.getZ() - client.player.getZ();
            if (dx * dx + dy * dy + dz * dz < minD2) continue;

            double ex = living.getX() - eye.x;
            double ey = living.getY() + living.getBbHeight() * 0.5 - eye.y;
            double ez = living.getZ() - eye.z;
            double len = Math.sqrt(ex * ex + ey * ey + ez * ez);
            double dot = len > 1e-6 ? (ex * look.x + ey * look.y + ez * look.z) / len : 1.0;
            double angle = Math.acos(Math.max(-1.0, Math.min(1.0, dot)));
            if (lastDrawnIds.contains(entry.getKey())) angle -= 0.08;   // 粘滞：上帧在画的优先进候选
            cands.add(new Cand(living, track, angle));
        }
        if (cands.isEmpty()) return result;
        cands.sort(Comparator.comparingDouble(c -> c.angle));

        int limit = Math.min(cands.size(), config.maxGhosts + 6);
        List<Ghost> ghosts = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            Cand cand = cands.get(i);
            LivingEntity living = cand.entity;
            Track track = cand.track;
            LeadPoint lp = computeLeadPoint(living, track, now, tauS);
            if (lp == null) {
                track.showing = false;
                continue;
            }
            if (lp.holdBack) {
                if (!track.showing) continue;
                if (!Double.isNaN(track.rx)) {
                    double ddx = track.rx - lp.x, ddz = track.rz - lp.z;
                    if (ddx * ddx + ddz * ddz < 0.12) {
                        track.showing = false;
                        continue;
                    }
                }
            } else {
                track.showing = true;
            }

            // 帧间平滑（移动 0.78 / 收缩 0.92）
            double smooth = lp.holdBack ? AimLeadRoundRules.RETURN_SMOOTH : AimLeadRoundRules.MOVING_SMOOTH;
            if (Double.isNaN(track.rx)) {
                track.rx = lp.x;
                track.ry = lp.y;
                track.rz = lp.z;
            } else {
                track.rx += (lp.x - track.rx) * smooth;
                track.ry += (lp.y - track.ry) * smooth;
                track.rz += (lp.z - track.rz) * smooth;
            }

            // L2：射线 vs 幽灵 hitbox
            double w = living.getBbWidth() * 0.5;
            AABB box = new AABB(track.rx - w, track.ry, track.rz - w,
                    track.rx + w, track.ry + living.getBbHeight(), track.rz + w);
            Vec3 end = eye.add(look.scale(RAY_RANGE));
            boolean hit = !lp.lowConf && (box.contains(eye) || box.clip(eye, end).isPresent());

            // 精确预测夹角终排
            double gx = track.rx - eye.x, gy = track.ry + living.getBbHeight() * 0.5 - eye.y, gz = track.rz - eye.z;
            double glen = Math.sqrt(gx * gx + gy * gy + gz * gz);
            double gdot = glen > 1e-6 ? (gx * look.x + gy * look.y + gz * look.z) / glen : 1.0;
            double angle = Math.acos(Math.max(-1.0, Math.min(1.0, gdot)));
            if (lastDrawnIds.contains(living.getId())) angle -= 0.08;
            ghosts.add(new Ghost(living, track.rx, track.ry, track.rz, angle, hit, lp.lowConf));
        }
        ghosts.sort(Comparator.comparingDouble(g -> g.angle));
        if (ghosts.size() > config.maxGhosts) ghosts = ghosts.subList(0, config.maxGhosts);

        lastDrawnIds.clear();
        boolean anyHit = false;
        for (Ghost ghost : ghosts) {
            lastDrawnIds.add(ghost.entity().getId());
            if (ghost.hit()) anyHit = true;
        }
        if (anyHit) fireNowAtMs = now;
        return ghosts;
    }

    /** 由轨迹样本算速度 → 外推 τ 的纯预测计算（渲染与 leadPointFor 共用）。 */
    private LeadPoint computeLeadPoint(LivingEntity entity, Track track, long now, double tauS) {
        int n = track.size();
        int i0 = track.idx(0);
        long tn = track.ts[i0];
        long stale = now - tn;
        if (stale > STALE_DROP_MS) return null;

        // 相邻样本对速度（新→旧），剔除尖峰（下落永不剔）
        int pairs = 0;
        for (int k = 0; k + 1 < n && pairs < 8; k++) {
            int a = track.idx(k), b = track.idx(k + 1);
            if (tn - track.ts[b] > 1_500L) break;
            double dt = (track.ts[a] - track.ts[b]) / 1000.0;
            if (dt < 0.03) continue;
            double wx = (track.xs[a] - track.xs[b]) / dt;
            double wy = (track.ys[a] - track.ys[b]) / dt;
            double wz = (track.zs[a] - track.zs[b]) / dt;
            if (AimLeadRoundRules.rejectVelocityPair(wx, wy, wz)) continue;
            pvx[pairs] = wx;
            pvy[pairs] = wy;
            pvz[pairs] = wz;
            pairs++;
        }

        boolean lowConf = false;
        double vx, vy, vz;
        if (pairs >= 2) {
            vx = median(pvx, 0, pairs);
            // 垂直速度用最新短窗中位数（上抛+下落混合时全窗中位数被抹平 → vy≈0 根因）
            vy = AimLeadRoundRules.verticalVelocity(pvy, pairs);
            vz = median(pvz, 0, pairs);
            track.lvx = vx;
            track.lvy = vy;
            track.lvz = vz;
            track.lvAt = now;
        } else if (now - track.lvAt < 2_500L) {
            vx = track.lvx;
            vy = track.lvy;
            vz = track.lvz;   // 有效对被尖峰吃光：沿用上个可信速度，灰框
            lowConf = true;
        } else {
            return null;
        }

        // 转向检测：新半窗 vs 旧半窗中位速度方向/速率
        if (pairs >= 4) {
            int half = pairs / 2;
            double nx = median(pvx, 0, half), nz = median(pvz, 0, half);
            double ox = median(pvx, half, pairs), oz = median(pvz, half, pairs);
            double sN = Math.sqrt(nx * nx + nz * nz), sO = Math.sqrt(ox * ox + oz * oz);
            if (sN > 0.5 && sO > 0.5) {
                double cos = (nx * ox + nz * oz) / (sN * sO);
                double ratio = sN / sO;
                if (cos < 0.766 || ratio < 0.4 || ratio > 2.5) lowConf = true;
            }
        }
        // 转向响应：lowConf 时改用最新 2 对速度外推 —— 方向立即拐弯
        if (lowConf && pairs >= 2) {
            int m = Math.min(2, pairs);
            vx = median(pvx, 0, m);
            vy = median(pvy, 0, m);
            vz = median(pvz, 0, m);
        }
        // 转向检测 + τ 缩放：打转/转向中缩短提前量，但框照常显示（低速时命中门槛大，
        // 把框甩在切线上反而必失）。角速度取最近 TURN_WINDOW_PAIRS 对的中位数抗噪；
        // 全为无效对（停在原地）时视为未转向。
        double turn = 0.0;
        {
            int m = Math.min(AimLeadRoundRules.TURN_WINDOW_PAIRS, pairs - 1);
            if (m >= 1) {
                double[] tr = turnScratch;
                int tc = 0;
                for (int k = 0; k + 2 < pairs && tc < m; k++) {
                    int a = track.idx(k), b = track.idx(k + 1), c = track.idx(k + 2);
                    double dtSeg = (track.ts[a] - track.ts[c]) / 1000.0;
                    double r = AimLeadRoundRules.turnDegPerSec(pvx[k], pvz[k], pvx[k + 1], pvz[k + 1], dtSeg);
                    if (r >= 0.0) {
                        tr[tc] = r;
                        tc++;
                    }
                }
                if (tc > 0) {
                    java.util.Arrays.sort(tr, 0, tc);
                    turn = (tc & 1) == 1 ? tr[tc / 2] : (tr[tc / 2 - 1] + tr[tc / 2]) * 0.5;
                }
            }
        }
        // 水平限幅（垂直不限：高空下落可达 60+）
        double horizSpeed = Math.sqrt(vx * vx + vz * vz);
        if (horizSpeed > MAX_SPEED) return null;

        double tauScale = AimLeadRoundRules.tauScale(turn);
        double effectiveTauS = tauS * tauScale;
        track.turnDegPerSec = turn;
        track.tauScale = tauScale;

        // 急停快路径：最近 STOP_WINDOW 内 3D 净位移 + 中位速度
        boolean justStopped = false;
        {
            int newest = i0;
            int older = -1;
            for (int k = 1; k < n; k++) {
                if (tn - track.ts[track.idx(k)] >= AimLeadRoundRules.STOP_WINDOW_MS) {
                    older = track.idx(k);
                    break;
                }
            }
            if (older >= 0) {
                double mdx = track.xs[newest] - track.xs[older];
                double mdy = track.ys[newest] - track.ys[older];
                double mdz = track.zs[newest] - track.zs[older];
                double displacement = Math.sqrt(mdx * mdx + mdy * mdy + mdz * mdz);
                double speed3d = Math.sqrt(vx * vx + vy * vy + vz * vz);
                if (AimLeadRoundRules.isStopped(tn - track.ts[older], displacement, speed3d)) {
                    justStopped = true;
                }
            }
        }

        double leadH = horizSpeed * tauS;
        double leadV = Math.abs(vy) * tauS;
        double speed3dNow = Math.sqrt(vx * vx + vy * vy + vz * vz);
        boolean holdBack = AimLeadRoundRules.shouldHoldBack(
                AimLeadRoundRules.isServerStale(stale, speed3dNow), justStopped, vy, leadH, leadV,
                track.showing);

        LeadPoint lp = new LeadPoint();
        lp.holdBack = holdBack;
        if (holdBack) {
            lp.x = track.xs[i0];
            lp.y = track.ys[i0];
            lp.z = track.zs[i0];   // 收缩目标 = 本体
            lp.lowConf = true;
            return lp;
        }
        double leadX = vx * effectiveTauS;
        double leadZ = vz * effectiveTauS;
        double footY = track.ys[i0];
        // 碰撞夹取：结果按轨迹缓存（120ms，或脚底换列立即失效——上台阶/贴墙时列变化最快）
        int ax = (int) Math.floor(track.xs[i0]);
        int az = (int) Math.floor(track.zs[i0]);
        double cdx, cdz;
        if (now - track.clampAtMs > 120L || ax != track.clAx || az != track.clAz) {
            double[] cl = clampLeadToCollision(entity,
                    new Vec3(track.xs[i0], footY, track.zs[i0]), leadX, leadZ);
            track.clx = cl[0];
            track.clz = cl[1];
            track.clampAtMs = now;
            track.clAx = ax;
            track.clAz = az;
        }
        cdx = track.clx;
        cdz = track.clz;
        lp.x = track.xs[i0] + cdx;
        lp.z = track.zs[i0] + cdz;
        double py = footY + vy * effectiveTauS;
        // 地形跟随：拿落点处方块的面高夹取预测 y —— 上下坡/台阶/半砖都跟着走。
        // 落点面高高出脚下抬升上限（> footY + 1.2）时不是可站立地形（墙/两格台阶），
        // 不夹取，交给 clampLeadToCollision 的水平截断处理。
        if (AimLeadRoundRules.needsTerrainClamp(py, footY)) {
            int gx = (int) Math.floor(track.xs[i0] + cdx);
            int gz = (int) Math.floor(track.zs[i0] + cdz);
            int gy = (int) Math.floor(footY);
            if (now - track.grAtMs > 300L
                    || track.grX != gx || track.grY0 != gy || track.grZ != gz) {
                track.grGround = surfaceAt(entity, gx, gz, gy);
                track.grX = gx;
                track.grY0 = gy;
                track.grZ = gz;
                track.grAtMs = now;
            }
            if (AimLeadRoundRules.withinFootRise(track.grGround, footY) && py < track.grGround) {
                py = track.grGround;
            }
        }
        lp.y = py;
        lp.lowConf = lowConf;
        storeLeadPrediction(track, lp, now, tauS);
        return lp;
    }

    /**
     * 把水平外推位移按世界碰撞截断——怪不能穿墙/半砖/破窗。
     *
     * <p>碰撞箱用【贴脚下板】（脚底起 {@link #FOOT_SLAB_H} 高，而非全身高）：全身高箱会把
     * 台阶、半砖当成墙提前截断，预测点卡在台阶下沿；脚下板贴合 MC 的台阶抬升语义——
     * 半砖（0.5）/台阶（0.5~0.6）能上，墙/窗仍然挡。超过一格的方块由
     * {@link #surfaceAt} 的面高判断拦下（面高超出抬升上限 → 不跟随）。
     * 异常回退为不截断（宁可穿也不崩渲染）。
     */
    private static double[] clampLeadToCollision(Entity entity, Vec3 origin, double dx, double dz) {
        try {
            Vec3 delta = new Vec3(dx, 0, dz);
            double w = entity.getBbWidth() * 0.5;
            AABB box = new AABB(origin.x - w, origin.y, origin.z - w,
                    origin.x + w, origin.y + FOOT_SLAB_H, origin.z + w);
            Vec3 allowed = Entity.collideBoundingBox(entity, delta, box, entity.level(),
                    Entity.collectAllColliders(entity, entity.level(), box.expandTowards(delta)));
            return new double[]{allowed.x, allowed.z};
        } catch (RuntimeException ignored) {
            return new double[]{dx, dz};
        }
    }

    /** 贴脚下板高度：只有脚底这一层参与水平碰撞，让框能跟着怪上台阶/上半砖/上坡。 */
    private static final double FOOT_SLAB_H = 0.3;

    /* ==================== 预测误差诊断（面板读数，不参与预测） ==================== */

    /**
     * 记录一次预测点，供 τ 后回检用。同一预测点在相邻帧重复出现时（预览帧与 Aimbot
     * 路径各调一次）不重复入队。
     */
    private static void storeLeadPrediction(Track track, LeadPoint lp, long now, double tauS) {
        if (now - track.lastPredRecordMs < 50L) return;
        track.lastPredRecordMs = now;
        int i = track.predCount % track.predAt.length;
        track.predX[i] = lp.x;
        track.predY[i] = lp.y;
        track.predZ[i] = lp.z;
        track.predAt[i] = now;
        track.predCount++;
    }

    /**
     * 回检：把 τ 前记录的预测位置与实体【当前】位置比对，得提前量误差（格）。
     *
     * <p>τ 就是"现在开枪、服务器结算时刻"的提前量，所以 τ 后实体所在位置才是正确落点；
     * 偏差序列就是预测精度本身。窗口限制在 0.6τ~2.5τ：更早的样本对应的测速输入已过期，
     * 更晚的没有意义。只保留最近 12 个样本。
     */
    private static void trackLeadError(LivingEntity entity, Track track, long now, double tauMs) {
        for (int i = 0; i < track.predCount; i++) {
            long at = track.predAt[i % track.predAt.length];
            if (at <= 0L) continue;
            long elapsed = now - at;
            if (elapsed < tauMs * 0.6) continue;
            if (elapsed > tauMs * 2.5) {
                track.predAt[i % track.predAt.length] = 0L;   // 过期样本作废
                continue;
            }
            if (entity.isRemoved()) continue;
            double dx = entity.getX() - track.predX[i % track.predAt.length];
            double dy = entity.getY() - track.predY[i % track.predAt.length];
            double dz = entity.getZ() - track.predZ[i % track.predAt.length];
            if (dx * dx + dy * dy + dz * dz > 4_000.0) {
                track.predAt[i % track.predAt.length] = 0L;   // 传送毛刺不算误差样本
                continue;
            }
            track.leadErrMs[track.leadErrIdx] = elapsed;
            track.leadErrBlocks[track.leadErrIdx] = Math.sqrt(dx * dx + dy * dy + dz * dz);
            track.leadErrIdx = (track.leadErrIdx + 1) % track.leadErrBlocks.length;
            track.leadErrCount++;
            track.predAt[i % track.predAt.length] = 0L;       // 一个样本只回检一次
        }
    }

    /**
     * 当前预测诊断快照（所有活跃轨迹的合并读数，面板显示用）。
     * 提前量误差 = τ 前预测点与实体现状的距离中位数（格）；
     * 角速度为场上最强转向目标的读数。
     */
    public LeadDiagnostics leadDiagnostics() {
        int n = 0;
        double bestTurn = 0.0;
        double[] buf = new double[24];
        for (Track track : tracks.values()) {
            if (track.turnDegPerSec > bestTurn) bestTurn = track.turnDegPerSec;
            int m = Math.min(track.leadErrCount, track.leadErrBlocks.length);
            for (int i = 0; i < m && n < buf.length; i++) {
                buf[n] = track.leadErrBlocks[i];
                n++;
            }
        }
        double err = Double.NaN;
        if (n > 0) {
            double[] sorted = java.util.Arrays.copyOf(buf, n);
            java.util.Arrays.sort(sorted);
            err = (n & 1) == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) * 0.5;
        }
        return new LeadDiagnostics(bestTurn, AimLeadRoundRules.isCircling(bestTurn), err, n);
    }

    /** 预测诊断（面板照实显示，不参与任何预测判断）。 */
    public record LeadDiagnostics(double turnDegPerSec, boolean circling,
                                  double leadErrBlocks, int leadErrSamples) {
    }

    /**
     * 指定方块列的落点面高（可站立表面 y = 方块顶面）或 {@link Double#NEGATIVE_INFINITY}。
     *
     * <p>扫描区间：floor(脚底 y) 向下 {@link #SURFACE_SCAN_DOWN} 格、向上
     * {@link #SURFACE_SCAN_UP} 格。向上要够到台阶/半砖（面高会高于脚底）；
     * 向下要够到下落怪即将落地的面。命中层【上方仍为非空碰撞】时判定为方块内部/墙脚，
     * 不是可站立面，返回 -∞ 不夹取（否则两格高的墙会把预测点抬到天上）。
     */
    private static double surfaceAt(LivingEntity entity, int x, int z, int y0) {
        try {
            var level = entity.level();
            int minY = level.getMinY();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = y0 + SURFACE_SCAN_UP; y >= y0 - SURFACE_SCAN_DOWN; y--) {
                if (y < minY) break;
                pos.set(x, y, z);
                var state = level.getBlockState(pos);
                if (state.getCollisionShape(level, pos).isEmpty()) continue;
                BlockPos above = pos.above();
                if (above.getY() <= level.getMaxY()
                        && !level.getBlockState(above).getCollisionShape(level, above).isEmpty()) {
                    return Double.NEGATIVE_INFINITY;
                }
                return y + 1.0;
            }
            return Double.NEGATIVE_INFINITY;
        } catch (RuntimeException ignored) {
            return Double.NEGATIVE_INFINITY;
        }
    }

    /** 落点面高扫描区间（格）：上探台阶/半砖，下探落地。 */
    private static final int SURFACE_SCAN_UP = 2;
    private static final int SURFACE_SCAN_DOWN = 4;

    /** 公开预瞄点（Magnet 幽灵框重建用）：当前 τ 的预测位置或 null。 */
    public AABB leadBoxFor(LivingEntity entity) {
        if (!enabled || entity == null || !isTarget(entity)) return null;
        Track track = tracks.get(entity.getId());
        if (track == null || track.size() == 0) return null;
        LeadPoint lp = computeLeadPoint(entity, track, System.currentTimeMillis(), tauMs() / 1000.0);
        if (lp == null) return null;
        double w = entity.getBbWidth() * 0.5;
        return new AABB(lp.x - w, lp.y, lp.z - w,
                lp.x + w, lp.y + entity.getBbHeight(), lp.z + w);
    }

    /** 与 Chams 同口径：敌对怪 + 狼 + 铁傀儡，凋零不算。 */
    static boolean isTarget(LivingEntity entity) {
        if (entity instanceof Player || entity instanceof WitherBoss) return false;
        return entity instanceof Enemy || entity instanceof Wolf || entity instanceof IronGolem;
    }

    /* ==================== 几何提交 ==================== */

    private static void submitWireBox(SubmitNodeCollector collector, PoseStack pose,
                                      double x, double y, double z, double w, double h,
                                      float r, float g, float b, float a) {
        double minX = x - w, maxX = x + w, minY = y, maxY = y + h, minZ = z - w, maxZ = z + w;
        // 12 条边，每条 {x1,y1,z1,x2,y2,z2}
        float[][] edges = {
                {(float) minX, (float) minY, (float) minZ, (float) maxX, (float) minY, (float) minZ},
                {(float) maxX, (float) minY, (float) minZ, (float) maxX, (float) minY, (float) maxZ},
                {(float) maxX, (float) minY, (float) maxZ, (float) minX, (float) minY, (float) maxZ},
                {(float) minX, (float) minY, (float) maxZ, (float) minX, (float) minY, (float) minZ},
                {(float) minX, (float) maxY, (float) minZ, (float) maxX, (float) maxY, (float) minZ},
                {(float) maxX, (float) maxY, (float) minZ, (float) maxX, (float) maxY, (float) maxZ},
                {(float) maxX, (float) maxY, (float) maxZ, (float) minX, (float) maxY, (float) maxZ},
                {(float) minX, (float) maxY, (float) maxZ, (float) minX, (float) maxY, (float) minZ},
                {(float) minX, (float) minY, (float) minZ, (float) minX, (float) maxY, (float) minZ},
                {(float) maxX, (float) minY, (float) minZ, (float) maxX, (float) maxY, (float) minZ},
                {(float) maxX, (float) minY, (float) maxZ, (float) maxX, (float) maxY, (float) maxZ},
                {(float) minX, (float) minY, (float) maxZ, (float) minX, (float) maxY, (float) maxZ}};
        collector.submitCustomGeometry(pose, LINE_TYPE, (stackPose, consumer) -> {
            for (float[] e : edges) {
                line(consumer, stackPose, e[0], e[1], e[2], e[3], e[4], e[5], r, g, b, a);
            }
        });
    }

    private static void submitFillBox(SubmitNodeCollector collector, PoseStack pose,
                                      double x, double y, double z, double w, double h,
                                      float r, float g, float b, float a) {
        double minX = x - w, maxX = x + w, minY = y, maxY = y + h, minZ = z - w, maxZ = z + w;
        collector.submitCustomGeometry(pose, FILL_TYPE, (stackPose, consumer) -> {
            // 4 侧面 + 顶面（各 2 三角）
            quad(consumer, stackPose, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, r, g, b, a);
            quad(consumer, stackPose, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
            quad(consumer, stackPose, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
            quad(consumer, stackPose, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);
            quad(consumer, stackPose, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        });
    }

    private static void submitLine(SubmitNodeCollector collector, PoseStack pose,
                                   double x1, double y1, double z1, double x2, double y2, double z2,
                                   float r, float g, float b, float a) {
        collector.submitCustomGeometry(pose, LINE_TYPE, (stackPose, consumer) ->
                line(consumer, stackPose,
                        (float) x1, (float) y1, (float) z1, (float) x2, (float) y2, (float) z2,
                        r, g, b, a));
    }

    private static void line(VertexConsumer consumer, PoseStack.Pose pose,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float r, float g, float b, float a) {
        consumer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a)
                .setNormal(pose, 0, 1, 0).setLineWidth(2.0f);
        consumer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a)
                .setNormal(pose, 0, 1, 0).setLineWidth(2.0f);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose,
                             double ax, double ay, double az, double bx, double by, double bz,
                             double cx, double cy, double cz, double dx, double dy, double dz,
                             float r, float g, float b, float a) {
        consumer.addVertex(pose, (float) ax, (float) ay, (float) az).setColor(r, g, b, a);
        consumer.addVertex(pose, (float) bx, (float) by, (float) bz).setColor(r, g, b, a);
        consumer.addVertex(pose, (float) cx, (float) cy, (float) cz).setColor(r, g, b, a);
        consumer.addVertex(pose, (float) ax, (float) ay, (float) az).setColor(r, g, b, a);
        consumer.addVertex(pose, (float) cx, (float) cy, (float) cz).setColor(r, g, b, a);
        consumer.addVertex(pose, (float) dx, (float) dy, (float) dz).setColor(r, g, b, a);
    }

    private static RenderType createFillType() {
        RenderPipeline pipeline = RenderPipeline.builder(RenderPipelinesAccess.micx$debugFilledSnippet())
                .withLocation(Identifier.fromNamespaceAndPath("micx-fabric", "pipeline/aim_lead_fill"))
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .build();
        RenderSetup setup = RenderSetup.builder(pipeline)
                .setOutputTarget(OutputTarget.MAIN_TARGET)
                .createRenderSetup();
        return RenderTypeAccess.micx$create("micx_aim_lead_fill", setup);
    }

    /** [from, to) 区间分量中位数（复用 medScratch 排序，n≤8）。 */
    private double median(double[] src, int from, int to) {
        int n = to - from;
        System.arraycopy(src, from, medScratch, 0, n);
        java.util.Arrays.sort(medScratch, 0, n);
        return (n & 1) == 1 ? medScratch[n / 2] : (medScratch[n / 2 - 1] + medScratch[n / 2]) * 0.5;
    }

    /* ==================== ping：game → tab → SLP → manual ==================== */

    public int effectivePing() {
        if (config.autoPing) {
            long now = System.currentTimeMillis();
            if (config.gameRtt && gameRtt > 0 && gameAt > 0L && now - gameAt < 45_000L) {
                return gameRtt;
            }
            int tab = tabPing();
            if (tab > 0) return tab;
            if (slpPing > 0 && now - slpAt < 30_000L) return slpPing;
        }
        return config.manualPing;
    }

    public String pingSource() {
        if (config.autoPing) {
            long now = System.currentTimeMillis();
            if (config.gameRtt && gameRtt > 0 && gameAt > 0L && now - gameAt < 45_000L) return "game RTT";
            if (tabPing() > 0) return "tab";
            if (slpPing > 0 && now - slpAt < 30_000L) return "slp";
        }
        return "manual";
    }

    private static int tabPing() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null || client.player == null) return 0;
        net.minecraft.client.multiplayer.PlayerInfo info =
                client.getConnection().getPlayerInfo(client.player.getUUID());
        return info == null ? 0 : Math.max(0, info.getLatency());
    }

    public int gameRtt() {
        return gameRtt;
    }

    public int gameJitter() {
        return gameJitter;
    }

    public int slpPingNow() {
        return slpPing;
    }

    public int tabPingNow() {
        return tabPing();
    }

    public int tauMs() {
        return (int) Math.max(50, Math.min(1_200, effectivePing() + config.extraMs));
    }

    /**
     * Raw RTT snapshot for timing-sensitive features. game RTT 需 ≥3 样本且 45s 内
     * 才算高置信（与 Forge 信心契约一致）；SLP/tab/manual 均低置信。
     */
    public synchronized LatencySnapshot latencySnapshot() {
        long now = System.currentTimeMillis();
        boolean freshGame = enabled && config.autoPing && config.gameRtt
                && gameRtt > 0 && gameRecentN >= 3 && gameAt > 0L
                && now - gameAt < 45_000L;
        if (freshGame) {
            return new LatencySnapshot(gameRtt, gameJitter, "game", true, gameAt);
        }
        return new LatencySnapshot(Math.max(0, effectivePing()), 0, pingSource(), false, 0L);
    }

    private synchronized void pushGame(int sampleMs) {
        if (sampleMs <= 0 || sampleMs >= 5_000) return;
        gameRecent[gameRecentN % gameRecent.length] = sampleMs;
        gameRecentN++;
        int n = Math.min(gameRecentN, gameRecent.length);
        int[] sorted = new int[n];
        System.arraycopy(gameRecent, 0, sorted, 0, n);
        java.util.Arrays.sort(sorted);
        gameRtt = sorted[n / 2];
        gameJitter = n <= 1 ? 0 : sorted[n - 1] - sorted[0];
        gameAt = System.currentTimeMillis();
    }

    private synchronized void clearLatencySamples() {
        java.util.Arrays.fill(gameRecent, 0);
        gameRecentN = 0;
        gameRtt = -1;
        gameJitter = 0;
        gameAt = 0L;
    }

    /* ---- SLP：后台线程按服务器列表协议测当前端点 RTT ---- */

    private void startSlp() {
        if (slpRun) return;
        slpRun = true;
        slpThread = new Thread(this::slpLoop, "micx-aimlead-slp");
        slpThread.setDaemon(true);
        slpThread.start();
    }

    private void stopSlp() {
        slpRun = false;
        if (slpThread != null) {
            slpThread.interrupt();
            slpThread = null;
        }
        clearSlp();
    }

    private synchronized void clearSlp() {
        java.util.Arrays.fill(slpRecent, 0);
        slpRecentN = 0;
        slpPing = 0;
        slpAt = 0L;
    }

    private void slpLoop() {
        while (slpRun) {
            try {
                Minecraft client = Minecraft.getInstance();
                if (config.autoPing && client != null && client.level != null
                        && client.player != null && client.getConnection() != null) {
                    InetSocketAddress addr = resolveAddr(client);
                    if (addr != null) {
                        int p = slpPingOnce(addr);
                        if (p > 0) pushSlp(p);
                    }
                }
            } catch (RuntimeException ignored) {
            }
            try {
                Thread.sleep(5_000L);
            } catch (InterruptedException interrupted) {
                return;
            }
        }
    }

    private static InetSocketAddress resolveAddr(Minecraft client) {
        try {
            SocketAddress remote = client.getConnection().getConnection().getRemoteAddress();
            if (remote instanceof InetSocketAddress address) return address;
            var server = client.getCurrentServer();
            if (server != null && server.ip != null) {
                String ip = server.ip;
                int port = 25565;
                int colon = ip.lastIndexOf(':');
                if (colon > 0 && ip.indexOf(':') == colon) {
                    try {
                        port = Integer.parseInt(ip.substring(colon + 1));
                        ip = ip.substring(0, colon);
                    } catch (NumberFormatException ignored) {
                    }
                }
                return new InetSocketAddress(ip, port);
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    /** 完整 SLP 序列：handshake(state=1) → status 请求/响应 → ping/pong，只计 ping→pong 段。 */
    private static int slpPingOnce(InetSocketAddress addr) {
        Socket sock = new Socket();
        try {
            sock.connect(addr, 1_500);
            sock.setSoTimeout(2_500);
            sock.setTcpNoDelay(true);
            DataOutputStream out = new DataOutputStream(sock.getOutputStream());
            DataInputStream in = new DataInputStream(sock.getInputStream());

            ByteArrayOutputStream hb = new ByteArrayOutputStream();
            DataOutputStream h = new DataOutputStream(hb);
            h.writeByte(0x00);
            writeVarInt(h, 47);
            byte[] host = addr.getHostString().getBytes("UTF-8");
            writeVarInt(h, host.length);
            h.write(host);
            h.writeShort(addr.getPort());
            writeVarInt(h, 1);
            writeVarInt(out, hb.size());
            out.write(hb.toByteArray());

            out.writeByte(0x01);   // frame len=1
            out.writeByte(0x00);   // status request
            out.flush();

            int len = readVarInt(in);
            if (len < 0 || len > (1 << 21)) return -1;
            skipFully(in, len);

            long t0 = System.nanoTime();
            out.writeByte(0x09);
            out.writeByte(0x01);
            out.writeLong(t0);
            out.flush();
            int plen = readVarInt(in);
            if (plen < 1 || plen > 64) return -1;
            skipFully(in, plen);
            return Math.max(1, (int) ((System.nanoTime() - t0) / 1_000_000L));
        } catch (IOException | RuntimeException exception) {
            return -1;
        } finally {
            try {
                sock.close();
            } catch (IOException ignored) {
            }
        }
    }

    private synchronized void pushSlp(int p) {
        slpRecent[slpRecentN % slpRecent.length] = p;
        slpRecentN++;
        int n = Math.min(slpRecentN, slpRecent.length);
        int[] tmp = new int[n];
        System.arraycopy(slpRecent, 0, tmp, 0, n);
        java.util.Arrays.sort(tmp);
        slpPing = tmp[n / 2];
        slpAt = System.currentTimeMillis();
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int length = 0;
        byte current;
        do {
            current = in.readByte();
            value |= (current & 0x7F) << (length * 7);
            length += 1;
            if (length > 5) throw new IOException("VarInt too big");
        } while ((current & 0x80) != 0);
        return value;
    }

    private static void skipFully(DataInputStream in, int n) throws IOException {
        long skipped = 0;
        while (skipped < n) {
            long s = in.skip(n - skipped);
            if (s <= 0) {
                if (in.read() < 0) throw new IOException("EOF");
                skipped++;
            } else {
                skipped += s;
            }
        }
    }

    /* ==================== 配置/生命周期 ==================== */

    private void loadConfig() {
        config.load();
    }

    public AimLeadConfig config() {
        loadConfig();
        return config;
    }

    public int markerOffsetX() {
        loadConfig();
        return config.markerOffsetX;
    }

    public int markerOffsetY() {
        loadConfig();
        return config.markerOffsetY;
    }

    public float markerScaleX() {
        loadConfig();
        return config.markerScaleX;
    }

    public float markerScaleY() {
        loadConfig();
        return config.markerScaleY;
    }

    public void setMarkerOffset(int x, int y) {
        loadConfig();
        config.markerOffsetX = Math.max(-2_000, Math.min(2_000, x));
        config.markerOffsetY = Math.max(-2_000, Math.min(2_000, y));
    }

    public void setMarkerScale(float x, float y) {
        loadConfig();
        config.markerScaleX = HudLayoutMath.clampScale(x);
        config.markerScaleY = HudLayoutMath.clampScale(y);
    }

    public void saveConfiguration() {
        loadConfig();
        config.save();
    }

    /* ==================== 数据结构 ==================== */

    public static final class LatencySnapshot {
        public final int rttMs;
        public final int jitterMs;
        public final String source;
        public final boolean highConfidence;
        public final long sampledAt;

        private LatencySnapshot(int rttMs, int jitterMs, String source,
                                boolean highConfidence, long sampledAt) {
            this.rttMs = Math.max(0, rttMs);
            this.jitterMs = Math.max(0, jitterMs);
            this.source = source == null ? "unknown" : source;
            this.highConfidence = highConfidence;
            this.sampledAt = sampledAt;
        }
    }

    private record Cand(LivingEntity entity, Track track, double angle) {
    }

    private record Ghost(Entity entity, double px, double py, double pz,
                         double angle, boolean hit, boolean lowConf) {
    }

    private static final class LeadPoint {
        double x, y, z;
        boolean lowConf;
        boolean holdBack;
    }

    /** 环形样本轨迹 + 渲染状态（lv* 兜底 / rx-平滑 / showing 滞回 / 碰撞与地面缓存 / L0 影子）。 */
    static final class Track {
        final long[] ts = new long[SAMPLE_CAP];
        final double[] xs = new double[SAMPLE_CAP];
        final double[] ys = new double[SAMPLE_CAP];
        final double[] zs = new double[SAMPLE_CAP];
        int count;

        long lastSeenMs = 0L;
        double lsx = Double.NaN, lsy, lsz;          // L0 影子（服务器最后报的坐标）
        double rx = Double.NaN, ry, rz;             // 渲染平滑位置
        boolean showing = false;                     // 显示滞回
        double lvx, lvy, lvz;                        // 上一个可信速度（尖峰吃光时兜底）
        long lvAt = 0L;
        double clx = 0.0, clz = 0.0;                 // 最近一次碰撞钳制增量
        long clampAtMs = 0L;
        int clAx = Integer.MIN_VALUE, clAz = Integer.MIN_VALUE;   // 钳制缓存的脚底列（换列立即失效）
        int grX = Integer.MIN_VALUE, grY0 = Integer.MIN_VALUE, grZ = Integer.MIN_VALUE;
        double grGround = Double.NEGATIVE_INFINITY;
        long grAtMs = 0L;

        /* ---- 转向检测（打转治乱）---- */
        double turnDegPerSec = 0.0;                  // 最近一次算出的角速度（诊断）
        double tauScale = 1.0;                       // 最近一次的提前量缩放

        /* ---- 提前量误差诊断（预测点 vs τ 后的真实位置）---- */
        final double[] leadErrMs = new double[12];
        final double[] leadErrBlocks = new double[12];
        int leadErrCount = 0;
        int leadErrIdx = 0;
        final double[] predX = new double[16];
        final double[] predZ = new double[16];
        final double[] predY = new double[16];
        final long[] predAt = new long[16];
        int predCount = 0;
        long lastPredRecordMs = 0L;

        void add(long t, double x, double y, double z) {
            int i = count % SAMPLE_CAP;
            ts[i] = t;
            xs[i] = x;
            ys[i] = y;
            zs[i] = z;
            count++;
        }

        int size() {
            return Math.min(count, SAMPLE_CAP);
        }

        /** 第 k 新的样本下标（k=0 最新）。 */
        int idx(int k) {
            return ((count - 1 - k) % SAMPLE_CAP + SAMPLE_CAP) % SAMPLE_CAP;
        }

        Vec3 latest() {
            if (size() == 0) return null;
            int i = idx(0);
            return new Vec3(xs[i], ys[i], zs[i]);
        }

        void setShadow(Vec3 position) {
            lsx = position.x;
            lsy = position.y;
            lsz = position.z;
        }
    }
}
