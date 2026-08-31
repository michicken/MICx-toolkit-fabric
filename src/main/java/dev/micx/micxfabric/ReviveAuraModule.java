package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

public final class ReviveAuraModule implements Module {
    private static final ReviveAuraModule INSTANCE = new ReviveAuraModule();
    private boolean enabled;
    private double range = 4.5;
    private double intervalMs = 200.0;
    private long lastRevive = 0L;
    private boolean configLoaded;

    private ReviveAuraModule() {}

    public static ReviveAuraModule instance() { return INSTANCE; }

    @Override public String id() { return "revive_aura"; }
    @Override public boolean defaultEnabled() { return false; }
    @Override public boolean enabled() { return enabled; }
    @Override public void setEnabled(boolean v) {
        loadConfig();
        enabled = v;
        ModuleStateStore.put(id(), v);
        if (v) { lastRevive = 0L; sendLogs = 0; scanLogAt = 0L; }
    }

    public double getRange() { loadConfig(); return range; }
    public void setRange(double v) { range = Math.max(1.0, Math.min(10.0, v)); saveConfig(); }
    public double getIntervalMs() { loadConfig(); return intervalMs; }
    public void setIntervalMs(double v) { intervalMs = Math.max(50.0, Math.min(1000.0, v)); saveConfig(); }

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
            for (Entity entity : client.level.entitiesForRendering()) {
                if (!(entity instanceof Player p) || p == client.player) continue;
                players++;
                if (isDownCandidate(p)) {
                    candidates++;
                    if (client.player.distanceTo(p) <= range) inRange++;
                }
            }
            if (diagAll || candidates > 0) {
                MicxFabric.LOGGER.info("[micx-revive] scan: players={} candidates={} inRange={} range={}", players, candidates, inRange, range);
            }
        }
        if (now - lastRevive < intervalMs) return;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof Player player)) continue;
            if (player == client.player) continue;
            if (!isDownCandidate(player)) continue;
            if (client.player.distanceTo(player) > range) continue;
            if (client.getConnection() == null) return;
            // 26.2 协议无"无坐标 INTERACT"（LpVec3 零向量=空坐标单字节哨兵，write 非空解引用），
            // Via 转 1.8.9 恒为 INTERACT_AT。location 对准目标 hitbox（原版右键同款相对偏移），
            // 不转头纯发包；Hyp 不校验视线遮挡。
            Vec3 eye = client.player.getEyePosition();
            Vec3 rel = player.getBoundingBox().clip(eye, player.position())
                    .map(v -> v.subtract(player.getX(), player.getY(), player.getZ()))
                    .orElse(Vec3.ZERO);
            client.getConnection().send(new ServerboundInteractPacket(player.getId(), InteractionHand.MAIN_HAND, rel, false));
            lastRevive = now;
            if (sendLogs++ < 10) {
                MicxFabric.LOGGER.info("[micx-revive] send -> {} dist={} rel=({},{},{}) sleeping={} pose={}",
                        player.getName().getString(), String.format("%.1f", client.player.distanceTo(player)),
                        String.format("%.2f", rel.x), String.format("%.2f", rel.y), String.format("%.2f", rel.z),
                        player.isSleeping(), player.getPose());
            }
            break;
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

    @Override public void resetState() { lastRevive = 0L; }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path cur = FabricRuntime.configPath().resolve("revive-aura.properties");
        Path leg = FabricRuntime.configPath().getParent().resolve("MICxToolkit_ReviveAura.cfg");
        Properties p = ConfigProperties.load(cur, leg);
        String rv = p.getProperty("range");
        if (rv != null) try { range = Math.max(1.0, Math.min(10.0, Double.parseDouble(rv.trim()))); } catch (Exception ignored) {}
        String iv = p.getProperty("intervalMs");
        if (iv != null) try { intervalMs = Math.max(50.0, Math.min(1000.0, Double.parseDouble(iv.trim()))); } catch (Exception ignored) {}
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
