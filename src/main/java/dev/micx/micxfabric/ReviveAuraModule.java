package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public final class ReviveAuraModule implements Module {
    private static final ReviveAuraModule INSTANCE = new ReviveAuraModule();
    private boolean enabled;
    private double range = 4.5;
    /** 对<b>同一目标</b>两次发包的最小间隔（毫秒）。0.2.110 起冷却分目标：A 在冷却不拖累 B。 */
    private double intervalMs = ReviveAuraRules.DEFAULT_INTERVAL_MS;
    /** 每个目标上次被发包的时间戳（entityId → 毫秒），只给还在视野里的玩家保留，离场即清。 */
    private final Map<Integer, Long> lastSentAtMs = new HashMap<>();
    private boolean configLoaded;
    /** Hyp 救援语义：对倒地玩家发一次 interact 即开始救援，服务端固定时长完成且不被打断，
     *  重复包无意义——每个目标每次倒地只发一包，起立/消失后解除标记可再次救援。
     *  2026-09-17（0.2.110）起冷却按目标分开：每 tick 最多发一包，给 A 发完 A 自己进冷却，
     *  没点过的 B 下一 tick 立刻可发，不再等对 A 的全局间隔；intervalMs 只挡对同一只的抖动连点。 */
    private final Set<Integer> attemptedIds = new HashSet<>();

    private ReviveAuraModule() {}

    public static ReviveAuraModule instance() { return INSTANCE; }

    @Override public String id() { return "revive_aura"; }
    @Override public boolean defaultEnabled() { return false; }
    @Override public boolean enabled() { return enabled; }
    @Override public void setEnabled(boolean v) {
        loadConfig();
        enabled = v;
        ModuleStateStore.put(id(), v);
        if (v) { attemptedIds.clear(); lastSentAtMs.clear(); sendLogs = 0; scanLogAt = 0L; }
    }

    public double getRange() { loadConfig(); return range; }
    public void setRange(double v) { range = Math.max(1.0, Math.min(10.0, v)); saveConfig(); }

    public double getIntervalMs() { loadConfig(); return intervalMs; }
    public void setIntervalMs(double v) { intervalMs = ReviveAuraRules.clampInterval(v); saveConfig(); }

    /** 诊断：扫描状态日志节流时间戳。 */
    private long scanLogAt;
    /** 诊断：已打过的发包日志条数（上限 10，enable 时清零）。 */
    private int sendLogs;

    /** 倒地判定：isSleeping() 在 26.2 对 Via 转来的 1.8.9 尸体可能恒 false，
     *  放宽为 Pose.SLEEPING 任一命中（1.8.9 无 Pose 元数据，靠 Via 映射，用日志实测验证）。 */
    private static boolean isDownCandidate(Player player) {
        return player.isSleeping()
                || player.getPose() == net.minecraft.world.entity.Pose.SLEEPING;
    }

    @Override public void tick(Minecraft client) {
        if (!enabled || client == null || client.player == null || client.level == null) return;
        long now = System.currentTimeMillis();
        // 扫描状态日志只在发现倒地候选时输出（平时零输出；-Dmicx.diag=true 恢复常开）
        boolean diagAll = Boolean.getBoolean("micx.diag");
        if (now - scanLogAt >= 3000L) {
            scanLogAt = now;
            int players = 0;
            int candidates = 0;
            int inRange = 0;
            int pending = 0;
            for (Entity entity : client.level.entitiesForRendering()) {
                if (!(entity instanceof Player p) || p == client.player) continue;
                players++;
                if (!isDownCandidate(p)) continue;
                candidates++;
                if (client.player.distanceTo(p) <= range) inRange++;
                if (!attemptedIds.contains(p.getId())) pending++;
            }
            if (diagAll || candidates > 0) {
                MicxFabric.LOGGER.info("[micx-revive] scan: players={} candidates={} inRange={} pending={} range={} interval={} cooled={}",
                        players, candidates, inRange, pending, range, intervalMs, lastSentAtMs.size());
            }
        }
        // 收集全部倒地候选并解除已起立/消失目标的标记；冷却表按「人还在不在视野」维护，离场即清。
        Set<Integer> downedIds = new HashSet<>();
        Set<Integer> presentIds = new HashSet<>();
        List<Player> pending = new ArrayList<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof Player player) || player == client.player) continue;
            presentIds.add(player.getId());
            if (!isDownCandidate(player)) continue;
            downedIds.add(player.getId());
            if (!attemptedIds.contains(player.getId()) && client.player.distanceTo(player) <= range) pending.add(player);
        }
        attemptedIds.retainAll(downedIds);
        lastSentAtMs.keySet().retainAll(presentIds);
        if (pending.isEmpty() || client.getConnection() == null) return;
        // 每 tick 最多一包（用户定稿 2026-09-17）；冷却分目标——只挡对同一只的连点，
        // 没点过的 B 永远立刻可选，A 的冷却不拖累 B。可发者里取最近的一只。
        double[] distances = new double[pending.size()];
        long[] lastSent = new long[pending.size()];
        for (int i = 0; i < pending.size(); i++) {
            distances[i] = client.player.distanceTo(pending.get(i));
            Long sent = lastSentAtMs.get(pending.get(i).getId());
            lastSent[i] = sent == null ? 0L : sent;
        }
        int pick = ReviveAuraRules.nextSendIndex(distances, lastSent, now, intervalMs);
        if (pick < 0) return;
        Player player = pending.get(pick);
        // 26.2 协议无"无坐标 INTERACT"（LpVec3 零向量=空坐标单字节哨兵，write 非空解引用），
        // Via 转 1.8.9 恒为 INTERACT_AT。location 对准目标 hitbox（原版右键同款相对偏移），
        // 不转头纯发包；Hyp 不校验视线遮挡。
        Vec3 eye = client.player.getEyePosition();
        Vec3 rel = player.getBoundingBox().clip(eye, player.position())
                .map(v -> v.subtract(player.getX(), player.getY(), player.getZ()))
                .orElse(Vec3.ZERO);
        client.getConnection().send(new ServerboundInteractPacket(player.getId(), InteractionHand.MAIN_HAND, rel, false));
        attemptedIds.add(player.getId());
        lastSentAtMs.put(player.getId(), now);
        if (sendLogs++ < 10) {
            MicxFabric.LOGGER.info("[micx-revive] send -> {} dist={} rel=({},{},{}) sleeping={} pose={} pending={} cooled={}",
                    player.getName().getString(), String.format("%.1f", client.player.distanceTo(player)),
                    String.format("%.2f", rel.x), String.format("%.2f", rel.y), String.format("%.2f", rel.z),
                    player.isSleeping(), player.getPose(), pending.size(), lastSentAtMs.size());
        }
    }

    /** 开关组合键（最多 3 键）：默认空绑定（Forge 2026-08-14 定稿：新模块禁止预设默认键）。 */
    private int[] toggleKeyCodes = KeyChord.EMPTY;

    @Override public int[] primaryChord() { loadConfig(); return toggleKeyCodes; }

    public void setToggleKeyCodes(int[] codes) {
        toggleKeyCodes = KeyChord.normalize(codes);
        saveConfig();
    }

    @Override public void onPrimaryPressed(net.minecraft.client.Minecraft client, boolean newlyEnabled) {
        if (!newlyEnabled) setEnabled(false);
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(dev.micx.micxfabric.ChatMessageStyles.notice(
                    "ReviveAura " + (enabled() ? "ON" : "OFF")));
        }
    }

    @Override public void resetState() { attemptedIds.clear(); lastSentAtMs.clear(); }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path cur = FabricRuntime.configPath().resolve("revive-aura.properties");
        Path leg = FabricRuntime.configPath().getParent().resolve("MICxToolkit_ReviveAura.cfg");
        Properties p = ConfigProperties.load(cur, leg);
        String rv = p.getProperty("range");
        if (rv != null) try { range = Math.max(1.0, Math.min(10.0, Double.parseDouble(rv.trim()))); } catch (Exception ignored) {}
        String iv = p.getProperty("intervalMs");
        if (iv != null) try { intervalMs = ReviveAuraRules.clampInterval(Double.parseDouble(iv.trim())); } catch (Exception ignored) {}
        toggleKeyCodes = KeyChord.readConfig(p, "toggleKeys", "toggleKey", 0);
    }

    private void saveConfig() {
        Properties p = new Properties();
        p.setProperty("range", Double.toString(range));
        p.setProperty("intervalMs", Double.toString(intervalMs));
        KeyChord.writeConfig(p, "toggleKeys", "toggleKey", toggleKeyCodes);
        try { AtomicProperties.store(FabricRuntime.configPath().resolve("revive-aura.properties"), p, "MICx ReviveAura"); } catch (IOException e) { MicxFabric.LOGGER.warn("Unable to save ReviveAura configuration", e); }
    }
}
