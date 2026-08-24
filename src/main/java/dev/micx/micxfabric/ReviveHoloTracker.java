package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 救援 holo 读取器（Forge ReviveHoloTracker 的移植）。
 *
 * <p>Hypixel 倒地玩家身体上方有服务端驱动的盔甲架 holo：
 * 含 SHIFT/SNEAK 提示 = 等待救援；只有计时行（如 {@code 8.0s}）= 正在被救，
 * 秒数是服务端真值。倒地后真实实体被尸体 NPC 替换 —— 通过
 * "不在 TAB 名单 + 皮肤纹理与存活记录一致且唯一" 定位身体。</p>
 */
public final class ReviveHoloTracker {
    private static final ReviveHoloTracker INSTANCE = new ReviveHoloTracker();
    private static final long SCAN_INTERVAL_MS = 100L;

    public enum State { NONE, WAITING, REVIVING }

    public record Info(State state, double seconds, double totalSec) {
        static Info NONE_I = new Info(State.NONE, -1D, -1D);
    }

    public static ReviveHoloTracker get() {
        return INSTANCE;
    }

    private final Map<String, Info> cache = new HashMap<>();
    private final Map<String, double[]> lastPos = new HashMap<>();
    private final Map<String, Identifier> lastSkin = new HashMap<>();
    private long lastScanMs;

    public Info get(String name) {
        Info info = cache.get(name);
        return info == null ? Info.NONE_I : info;
    }

    public void clear() {
        cache.clear();
        lastPos.clear();
        lastSkin.clear();
    }

    /** 渲染帧调用（内部 100ms 节流）。 */
    public void tick(Minecraft client, ZombiesTracker tracker) {
        if (client == null || client.level == null || client.player == null) return;
        if (!tracker.isInZombies()) {
            if (!cache.isEmpty()) clear();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastScanMs < SCAN_INTERVAL_MS) return;
        lastScanMs = now;

        recordAlivePositions(client, tracker);
        cache.clear();

        List<ReviveHoloRules.HoloLine> lines = scanArmorStands(client);
        List<ReviveHoloRules.Cluster> clusters = ReviveHoloRules.clusters(lines);
        if (clusters.isEmpty()) return;

        List<ReviveHoloRules.Body> bodies = findDownedBodies(client, tracker);
        if (bodies.isEmpty()) return;

        for (ReviveHoloRules.Match m : ReviveHoloRules.match(bodies, clusters, ReviveHoloRules.MATCH_MAX)) {
            if (m.reviving) {
                Info prev = cache.get(m.name);
                double total = prev != null && prev.state() == State.REVIVING
                        ? Math.max(prev.totalSec(), m.seconds) : m.seconds;
                cache.put(m.name, new Info(State.REVIVING, m.seconds, total));
            } else {
                cache.put(m.name, new Info(State.WAITING, -1D, -1D));
            }
        }
    }

    /** 存活时记录坐标 + 皮肤纹理（倒地后定位尸体用）。 */
    private void recordAlivePositions(Minecraft client, ZombiesTracker tracker) {
        Map<String, String> statuses = tracker.frame().playerStatuses();
        for (AbstractClientPlayer player : client.level.players()) {
            String name = player.getName().getString();
            String st = statuses.get(name);
            if ("down".equals(st) || "dead".equals(st) || "quit".equals(st)) continue;
            if (player == client.player) {
                lastPos.put(name, new double[]{player.getX(), player.getZ()});
                continue;
            }
            lastPos.put(name, new double[]{player.getX(), player.getZ()});
            Identifier skin = skinOf(player);
            if (skin != null) lastSkin.put(name, skin);
        }
    }

    private static Identifier skinOf(AbstractClientPlayer player) {
        net.minecraft.world.entity.player.PlayerSkin skin = player.getSkin();
        return skin == null || skin.body() == null ? null : skin.body().texturePath();
    }

    private List<ReviveHoloRules.HoloLine> scanArmorStands(Minecraft client) {
        List<ReviveHoloRules.HoloLine> lines = new ArrayList<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand)) continue;
            Component name = stand.getCustomName();
            if (name == null) continue;
            String text = name.getString();
            if (text == null || text.trim().isEmpty()) continue;
            ReviveHoloRules.HoloLine line =
                    ReviveHoloRules.parseLine(stand.getX(), stand.getZ(), text);
            if (line != null) lines.add(line);
        }
        return lines;
    }

    /**
     * 倒地队友的身体：自己 = 本地玩家；队友 = TAB 外皮肤匹配的尸体 NPC；
     * 兜底 = 存活时最后已知坐标。
     */
    private List<ReviveHoloRules.Body> findDownedBodies(Minecraft client, ZombiesTracker tracker) {
        List<ReviveHoloRules.Body> bodies = new ArrayList<>();
        Set<String> downNames = new HashSet<>();
        for (Map.Entry<String, String> e : tracker.frame().playerStatuses().entrySet()) {
            if ("down".equals(e.getValue())) downNames.add(e.getKey());
        }
        if (downNames.isEmpty()) return bodies;

        Set<String> tabNames = new HashSet<>();
        if (client.getConnection() != null) {
            for (PlayerInfo info : client.getConnection().getOnlinePlayers()) {
                tabNames.add(info.getProfile().name());
            }
        }

        String selfName = client.player.getName().getString();
        for (String name : downNames) {
            if (name.equals(selfName)) {
                bodies.add(new ReviveHoloRules.Body(name, client.player.getX(), client.player.getZ()));
                continue;
            }
            AbstractClientPlayer corpse = findCorpseBySkin(client, name, tabNames);
            if (corpse != null) {
                bodies.add(new ReviveHoloRules.Body(name, corpse.getX(), corpse.getZ()));
                continue;
            }
            double[] pos = lastPos.get(name);
            if (pos != null) {
                bodies.add(new ReviveHoloRules.Body(name, pos[0], pos[1]));
            }
        }
        return bodies;
    }

    /** 尸体 NPC：该皮肤在存活记录里唯一且对应该名字。 */
    private AbstractClientPlayer findCorpseBySkin(Minecraft client, String name, Set<String> tabNames) {
        Identifier want = lastSkin.get(name);
        if (want == null) return null;
        AbstractClientPlayer found = null;
        int matches = 0;
        for (AbstractClientPlayer player : client.level.players()) {
            if (tabNames.contains(player.getName().getString())) continue;
            Identifier skin = skinOf(player);
            if (skin == null || !skin.equals(want)) continue;
            found = player;
            matches++;
        }
        return matches == 1 ? found : null;
    }
}
