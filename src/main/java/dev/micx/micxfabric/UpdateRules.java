package dev.micx.micxfabric;

/**
 * 自动更新的纯规则：版本比较、该不该装、文件名归属。
 *
 * <p>全是无副作用函数，可以脱离游戏直接单测——换装那一步（真正动 mods/）在
 * {@link UpdateInstaller} 里，单独隔离。
 */
public final class UpdateRules {
    /**
     * 发布时间之后要再等多久才允许安装。给发版留一个撤回窗口：推上去发现翻车时，
     * 只要在窗口内把服务端清单改回去，就没人装上。
     */
    public static final long DEFAULT_DELAY_MS = 30L * 60L * 1000L;

    /**
     * 只认这个前缀的文件名才算「自己的 jar」。
     *
     * <p>mods/ 里还有 SkillShare、ZBHelpStart 等别人的产物，换装只允许碰自己这一个文件，
     * 任何按通配的删除都可能把它们一起干掉。
     */
    public static final String JAR_PREFIX = "micx-fabric-";

    private UpdateRules() {
    }

    /**
     * 点分数字版本比较：{@code a} 比 {@code b} 新返回正数，旧返回负数，相同返回 0。
     * 缺的段按 0 补（{@code "1.2"} == {@code "1.2.0"}）；非纯数字的段退回字典序，
     * 让带后缀的版本号（{@code 0.2.123-rc1}）仍然可以比较而不会抛异常。
     */
    public static int compare(String a, String b) {
        if (a == null || b == null) return 0;
        String[] left = a.trim().split("\\.");
        String[] right = b.trim().split("\\.");
        int segments = Math.max(left.length, right.length);
        for (int i = 0; i < segments; i++) {
            String l = i < left.length ? left[i] : "0";
            String r = i < right.length ? right[i] : "0";
            long ln = parseSegment(l);
            long rn = parseSegment(r);
            int result = (ln >= 0 && rn >= 0) ? Long.compare(ln, rn) : l.compareTo(r);
            if (result != 0) return result;
        }
        return 0;
    }

    /**
     * 是否应该下载并安装：远端确实更新，并且已经过了 {@code publishedAt + delay} 这个时刻。
     * 缺发布时间（{@code <= 0}）视为立即生效；{@code nowMs} 就用本地时钟，
     * 所以服务端改清单对所有人生效于同一时刻。
     */
    public static boolean installable(String localVersion, String remoteVersion,
                                      long publishedAtMs, long nowMs, long delayMs) {
        if (compare(remoteVersion, localVersion) <= 0) return false;
        if (publishedAtMs <= 0L) return true;
        return nowMs - publishedAtMs >= Math.max(0L, delayMs);
    }

    /** 文件名是否属于本 mod（前缀 + .jar 后缀）。 */
    public static boolean isOwnJar(String fileName) {
        if (fileName == null) return false;
        String name = fileName.trim();
        return name.startsWith(JAR_PREFIX) && name.endsWith(".jar") && name.length() > JAR_PREFIX.length() + 4;
    }

    /** 让位时旧 jar 改名成什么。保留一份，翻车能退回来。 */
    public static String backupName(String jarFileName) {
        return jarFileName + ".bak";
    }

    /** 判断一个 64 位十六进制串是不是合法的 sha256。 */
    public static boolean isSha256(String value) {
        if (value == null || value.length() != 64) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) return false;
        }
        return true;
    }

    /** 解析版本段；非纯数字或过大返回 -1，表示「退回字符串比较」。 */
    private static long parseSegment(String segment) {
        if (segment == null || segment.isEmpty()) return -1;
        long value = 0;
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c < '0' || c > '9') return -1;
            value = value * 10 + (c - '0');
            if (value > 1_000_000L) return -1;
        }
        return value;
    }
}
