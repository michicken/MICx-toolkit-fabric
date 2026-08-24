package dev.micx.micxfabric;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 救援 holo 读取规则（B 模式）— 纯逻辑，无 Minecraft 依赖，可单测。
 *
 * <p>机制移植自 Hypixel-Zombies-Mod (mc-26.1.2) {@code TeammateTracker.detectBeingRevived}：
 * <ul>
 *   <li>Hypixel 倒地玩家身体上方有一摞隐形盔甲架 holo（服务端驱动的文字）：
 *       一行提示（"按住SHIFT…"） + 一行计时（{@code 24.0s}）。</li>
 *   <li>含 {@code SHIFT}/{@code SNEAK} 提示 = 等待救援；只有计时行 = 正在被救（秒数是服务端真值）。</li>
 *   <li>救援中断/重按/换人救：服务端重置 holo 计时，下次扫描自然读到新值。</li>
 * </ul>
 */
public final class ReviveHoloRules {

    /** 计时行：整行匹配 {@code 24.0s} / {@code 0.6s} / {@code 12s}（matches，非 find）。 */
    public static final Pattern TIMER_LINE = Pattern.compile("\\d+(\\.\\d+)?s");

    /** 同一摞 holo 的水平合并半径（格）。 */
    public static final double CLUSTER_XZ = 0.6D;

    /** 倒地者身体 ↔ 救援簇 的最大匹配水平距离（格）。 */
    public static final double MATCH_MAX = 1.5D;

    private ReviveHoloRules() {}

    /** 一行 holo 文字（单个盔甲架名牌，剥码后）。 */
    public static final class HoloLine {
        public final double x, z;
        public final boolean hasShift;
        /** 整行是计时行时的秒数；非计时行 null。 */
        public final Double timerSeconds;

        public HoloLine(double x, double z, boolean hasShift, Double timerSeconds) {
            this.x = x;
            this.z = z;
            this.hasShift = hasShift;
            this.timerSeconds = timerSeconds;
        }
    }

    /** 救援簇：同一 xz 上方一摞 holo 的聚合。 */
    public static final class Cluster {
        public final double x, z;
        public final boolean hasShift;
        public final double timerSeconds;

        public Cluster(double x, double z, boolean hasShift, double timerSeconds) {
            this.x = x;
            this.z = z;
            this.hasShift = hasShift;
            this.timerSeconds = timerSeconds;
        }
    }

    /** 倒地者身体（已知坐标）。 */
    public static final class Body {
        public final String name;
        public final double x, z;

        public Body(String name, double x, double z) {
            this.name = name;
            this.x = x;
            this.z = z;
        }
    }

    /** 倒地者 ↔ 簇 匹配结果。 */
    public static final class Match {
        public final String name;
        /** true = 正在被救（簇无 SHIFT 提示）；false = 等待救援。 */
        public final boolean reviving;
        /** 服务端真值秒数（簇计时行）。 */
        public final double seconds;

        public Match(String name, boolean reviving, double seconds) {
            this.name = name;
            this.reviving = reviving;
            this.seconds = seconds;
        }
    }

    /**
     * 救援进度（0~1）：已救援比例 = (总时长 - 剩余) / 总时长。
     * 服务端 holo 计时为"剩余秒数"倒数（救援开始显示总时长，如 8.0s → 0），
     * 总时长取救援会话开始瞬间读到的值；总时长未知（≤0）时返回 0.5（中性占位，
     * 下一扫描周期读到初始值即恢复真实进度）。
     */
    public static float reviveProgress(double totalSec, double remainSec) {
        if (totalSec <= 0.0D) return 0.5f;
        if (remainSec <= 0.0D) return 1.0f;
        double p = (totalSec - remainSec) / totalSec;
        return (float) Math.max(0.0D, Math.min(1.0D, p));
    }

    /** 剥 § 格式码（§x 两位）。 */
    public static String stripFormatting(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        boolean skipNext = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (skipNext) { skipNext = false; continue; }
            if (c == '\u00A7') { skipNext = true; continue; }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * 解析一行 holo 文字。空/剥码后为空 → null。
     * 同时标记 SHIFT/SNEAK 提示与整行计时（两者可同在一行，互不排斥）。
     */
    public static HoloLine parseLine(double x, double z, String raw) {
        if (raw == null) return null;
        String t = stripFormatting(raw).trim();
        if (t.isEmpty()) return null;
        String upper = t.toUpperCase(Locale.ROOT);
        boolean hasShift = upper.contains("SHIFT") || upper.contains("SNEAK");
        Double timer = null;
        if (TIMER_LINE.matcher(t).matches()) {
            try {
                timer = Double.parseDouble(t.substring(0, t.length() - 1));
            } catch (NumberFormatException ignored) {
                timer = null;
            }
        }
        return new HoloLine(x, z, hasShift, timer);
    }

    /** 把 holo 行聚成簇（XZ ≤ CLUSTER_XZ 合并），只保留含计时行的救援簇。 */
    public static List<Cluster> clusters(List<HoloLine> lines) {
        List<Cluster> out = new ArrayList<Cluster>();
        if (lines == null || lines.isEmpty()) return out;
        // 第一遍：全部行并入簇（含无计时的 SHIFT 提示行），簇计时暂以 NaN 表示未知
        for (HoloLine line : lines) {
            Cluster c = null;
            for (Cluster ex : out) {
                double dx = ex.x - line.x, dz = ex.z - line.z;
                if (dx * dx + dz * dz <= CLUSTER_XZ * CLUSTER_XZ) { c = ex; break; }
            }
            if (c == null) {
                out.add(new Cluster(line.x, line.z, line.hasShift,
                        line.timerSeconds == null ? Double.NaN : line.timerSeconds));
            } else {
                int idx = out.indexOf(c);
                boolean shift = c.hasShift || line.hasShift;
                double timer = c.timerSeconds;
                if (line.timerSeconds != null) timer = line.timerSeconds;
                out.set(idx, new Cluster(c.x, c.z, shift, timer));
            }
        }
        // 第二遍：只保留救援簇（有计时行）
        java.util.Iterator<Cluster> it = out.iterator();
        while (it.hasNext()) {
            if (Double.isNaN(it.next().timerSeconds)) it.remove();
        }
        return out;
    }

    /**
     * 倒地者 ↔ 救援簇 1:1 最近贪心匹配（每个簇/每个倒地者只使用一次）。
     * 返回按距离升序消费的匹配列表；未匹配到的倒地者不出现在结果里。
     */
    public static List<Match> match(List<Body> bodies, List<Cluster> clusters, double maxDist) {
        List<Match> result = new ArrayList<Match>();
        if (bodies == null || bodies.isEmpty() || clusters == null || clusters.isEmpty()) return result;

        double max2 = maxDist * maxDist;
        List<double[]> pairs = new ArrayList<double[]>(); // {dist2, bodyIdx, clusterIdx}
        for (int i = 0; i < bodies.size(); i++) {
            Body b = bodies.get(i);
            for (int j = 0; j < clusters.size(); j++) {
                Cluster c = clusters.get(j);
                double dx = c.x - b.x, dz = c.z - b.z;
                double d2 = dx * dx + dz * dz;
                if (d2 <= max2) pairs.add(new double[] {d2, i, j});
            }
        }
        pairs.sort(new java.util.Comparator<double[]>() {
            @Override public int compare(double[] a, double[] b) {
                return Double.compare(a[0], b[0]);
            }
        });

        boolean[] usedBody = new boolean[bodies.size()];
        boolean[] usedCluster = new boolean[clusters.size()];
        for (double[] p : pairs) {
            int bi = (int) p[1], ci = (int) p[2];
            if (usedBody[bi] || usedCluster[ci]) continue;
            usedBody[bi] = true;
            usedCluster[ci] = true;
            Body b = bodies.get(bi);
            Cluster c = clusters.get(ci);
            result.add(new Match(b.name, !c.hasShift, c.timerSeconds));
        }
        return result;
    }
}
