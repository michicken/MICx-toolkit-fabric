package dev.micx.micxfabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 自动更新：启动后从服务器清单里查新版，校验通过就把自己换掉（重启生效）。
 *
 * <p>换装这件事只有在「重新开始扫描 mods/ 之前」改文件才有意义，所以流程是
 * <b>本次会话下载并换装，下次启动跑新版</b>：本次游戏仍然跑旧 jar，用户重启一次即完成。
 *
 * <p>所有网络和文件操作都在这条 daemon 线程上，绝不在渲染线程做 I/O；需要发给玩家看的话
 * 先攒进 {@link #pendingChat}，由 {@link #tick} 在主线程发出去。
 *
 * <p>失败一律保持现状可玩：查不到、校验不过、旧 jar 被占用，都只影响「这次没更新成功」。
 */
public final class UpdateModule implements Module {
    /** 服务器清单地址（与 Forge 侧 VersionGate 同一个文件，靠新增的 files 段区分产品）。 */
    public static final String MANIFEST_URL = "https://zombie.nienie.fun/zombies/version.json";
    /** 清单里的相对地址按这个站点拼。 */
    public static final String SITE_URL = "https://zombie.nienie.fun";

    /** 启动后多久开始第一次检查：让启动流程先跑完。 */
    private static final long FIRST_CHECK_DELAY_MS = 15_000L;
    /** 同一次会话内的复查间隔。 */
    private static final long RECHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L;
    private static final int TIMEOUT_MS = 10_000;
    private static final int MAX_MANIFEST_BYTES = 64 * 1024;
    private static final long MAX_JAR_BYTES = 64L * 1024L * 1024L;

    private static final UpdateModule INSTANCE = new UpdateModule();

    /** 对外可见的更新状态。 */
    public enum State {
        IDLE,        // 还没查
        CHECKING,    // 正在查清单
        UP_TO_DATE,  // 服务器没有更新（或已关自动安装）
        DOWNLOADING, // 正在下新版
        INSTALLED,   // 换装完成，等重启
        LOCKED,      // 下好了但旧 jar 占着位，需要手动放一次
        FAILED       // 这次没成功，不影响玩
    }

    private boolean enabled;
    private boolean autoInstall = true;
    /** 非空时给下载请求带上 {@code ?t=} 签名 token；留空表示服务器没给下载加门槛。 */
    private String tokenUrl = "";
    private boolean configLoaded;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "MICx-Update");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean busy = new AtomicBoolean();

    private volatile State state = State.IDLE;
    private volatile String localVersion = "?";
    private volatile String remoteVersion = "";
    private volatile String detail = "";
    private volatile long lastCheckMs;
    private volatile Path stagedJar;
    private volatile String pendingChat;
    private long firstTickMs;
    private boolean doubleJarWarned;

    private UpdateModule() {
    }

    public static UpdateModule instance() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return "auto_update";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public boolean enabled() {
        loadConfig();
        return enabled;
    }

    @Override
    public void setEnabled(boolean value) {
        loadConfig();
        enabled = value;
        ModuleStateStore.put(id(), value);
        if (value) {
            firstTickMs = 0L;
            lastCheckMs = 0L;
            state = State.IDLE;
            detail = "";
        }
    }

    public boolean autoInstall() {
        loadConfig();
        return autoInstall;
    }

    public void setAutoInstall(boolean value) {
        loadConfig();
        autoInstall = value;
        saveConfig();
    }

    public String tokenUrl() {
        loadConfig();
        return tokenUrl;
    }

    public void setTokenUrl(String value) {
        loadConfig();
        tokenUrl = value == null ? "" : value.trim();
        saveConfig();
    }

    public State state() {
        return state;
    }

    public String localVersion() {
        return localVersion;
    }

    public String remoteVersion() {
        return remoteVersion;
    }

    public String detail() {
        return detail;
    }

    public long lastCheckMs() {
        return lastCheckMs;
    }

    /** 换装后留在暂存区的那份新 jar，用来告诉玩家手动放哪个文件。 */
    public Path stagedJar() {
        return stagedJar;
    }

    /** 暂存目录：`config/MICxToolkit/update/`。 */
    public Path updateDir() {
        return FabricRuntime.configPath().resolve("update");
    }

    /** 供配置页的「立即检查」用：清掉节流并触发一次。 */
    public void requestCheck() {
        if (!enabled()) return;
        lastCheckMs = 0L;
        firstTickMs = 0L;
        startCheck();
    }

    @Override
    public void tick(Minecraft client) {
        loadConfig();
        if (!enabled) return;
        announcePending(client);
        warnAboutDuplicateJars(client);
        long now = System.currentTimeMillis();
        if (firstTickMs == 0L) firstTickMs = now;
        if (lastCheckMs == 0L) {
            if (now - firstTickMs < FIRST_CHECK_DELAY_MS) return;
        } else if (now - lastCheckMs < RECHECK_INTERVAL_MS) {
            return;
        }
        startCheck();
    }

    @Override
    public void resetState() {
        state = State.IDLE;
        detail = "";
        lastCheckMs = 0L;
        firstTickMs = 0L;
        doubleJarWarned = false;
    }

    private void startCheck() {
        if (!busy.compareAndSet(false, true)) return;
        state = State.CHECKING;
        worker.submit(this::run);
    }

    /** 后台线程主体：查清单 → 判断 → 下载 → 校验 → 换装。 */
    private void run() {
        try {
            localVersion = currentVersion();
            String json = readText(MANIFEST_URL, MAX_MANIFEST_BYTES, false);
            UpdateManifest manifest = UpdateManifest.parse(json, UpdateManifest.PLATFORM_FABRIC);
            if (!manifest.usable()) {
                fail("清单不可用：" + String.join("；", manifest.problems()));
                return;
            }
            remoteVersion = manifest.version();
            long now = System.currentTimeMillis();
            if (!UpdateRules.installable(localVersion, remoteVersion,
                    manifest.publishedAtMs(), now, manifest.delayMs())) {
                state = State.UP_TO_DATE;
                detail = "已是最新";
                return;
            }
            if (!autoInstall) {
                state = State.UP_TO_DATE;
                detail = "服务器有 " + remoteVersion + "（已关闭自动安装）";
                queueChat("服务器有新版 " + remoteVersion + "，自动安装已关闭（面板可手动更新）");
                return;
            }
            state = State.DOWNLOADING;
            detail = "正在下载 " + remoteVersion;
            Path staged = downloadAndVerify(manifest);
            if (staged == null) return;

            Path self = ownJarPath();
            if (self == null) {
                state = State.LOCKED;
                detail = "拿不到当前 jar 路径，无法自动换装";
                queueChat("新版 " + remoteVersion + " 已下载，但无法自动换装；文件在 " + staged);
                return;
            }
            Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
            UpdateInstaller.Result result = UpdateInstaller.install(
                    modsDir, staged, manifest.entry().fileName(), self, manifest.entry().size());
            switch (result) {
                case INSTALLED, ALREADY_STAGED -> {
                    state = State.INSTALLED;
                    detail = "已换装到 " + remoteVersion;
                    queueChat("已更新到 " + remoteVersion + "，重启游戏生效");
                }
                case LOCKED -> {
                    state = State.LOCKED;
                    detail = "旧 jar 被占用，需要手动放一次";
                    queueChat("新版 " + remoteVersion + " 已下载，但旧文件被占用（关掉游戏后手动替换即可）。文件：" + staged);
                }
                default -> fail("换装失败：" + result);
            }
        } catch (Throwable failure) {
            fail("检查失败：" + failure.getClass().getSimpleName());
            MicxFabric.LOGGER.warn("自动更新检查失败", failure);
        } finally {
            lastCheckMs = System.currentTimeMillis();
            busy.set(false);
        }
    }

    /** 下载到暂存区并校验 sha256 与大小；通过返回暂存文件，不通过返回 null。 */
    private Path downloadAndVerify(UpdateManifest manifest) throws IOException {
        UpdateManifest.Entry entry = manifest.entry();
        Path dir = updateDir();
        Files.createDirectories(dir);
        Path staged = dir.resolve(entry.fileName());
        if (Files.isRegularFile(staged) && UpdateInstaller.matchesSha256(staged, entry.sha256())) {
            stagedJar = staged;
            return staged;
        }
        Files.deleteIfExists(staged);

        Path part = dir.resolve(entry.fileName() + ".part");
        Files.deleteIfExists(part);
        String url = manifest.absoluteUrl(SITE_URL);
        download(url, part);
        long size = Files.size(part);
        if (size <= 0 || size > MAX_JAR_BYTES) {
            Files.deleteIfExists(part);
            fail("下载体积不对：" + size);
            return null;
        }
        if (entry.size() > 0 && size != entry.size()) {
            Files.deleteIfExists(part);
            fail("下载大小与清单不一致：期望 " + entry.size() + "，实际 " + size);
            return null;
        }
        if (!UpdateInstaller.matchesSha256(part, entry.sha256())) {
            Files.deleteIfExists(part);
            fail("下载内容校验不过，已丢弃");
            return null;
        }
        Files.move(part, staged, StandardCopyOption.REPLACE_EXISTING);
        stagedJar = staged;
        return staged;
    }

    private void download(String url, Path target) throws IOException {
        try (InputStream input = open(url, MAX_JAR_BYTES, true);
             OutputStream output = Files.newOutputStream(target)) {
            input.transferTo(output);
        }
    }

    private String readText(String url, int maxBytes, boolean withToken) throws IOException {
        try (InputStream input = open(url, maxBytes, withToken)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 打开一个带体积上限的 HTTPS 流。{@code withToken} 为真且配了 tokenUrl 时，
     * 先取一个签名 token 附在查询串上——服务器若要给下载加门槛，就靠这一步。
     */
    private InputStream open(String url, long maxBytes, boolean withToken) throws IOException {
        String target = url;
        if (withToken && !tokenUrl.isBlank()) {
            String token = fetchToken();
            if (!token.isBlank()) {
                target = url + (url.contains("?") ? "&" : "?") + "t=" + token;
            }
        }
        HttpsURLConnection connection = (HttpsURLConnection) URI.create(target).toURL().openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "MICx-Fabric-Updater");
        int code = connection.getResponseCode();
        if (code != HttpsURLConnection.HTTP_OK) {
            connection.disconnect();
            throw new IOException("HTTP " + code);
        }
        return new BoundedInputStream(connection, connection.getInputStream(), maxBytes);
    }

    private String fetchToken() {
        try {
            String body = readText(tokenUrl, 16 * 1024, false);
            int start = body.indexOf("\"token\"");
            if (start < 0) return "";
            int colon = body.indexOf(':', start);
            if (colon < 0) return "";
            int open = body.indexOf('"', colon + 1);
            if (open < 0) return "";
            int close = body.indexOf('"', open + 1);
            return close < 0 ? "" : body.substring(open + 1, close);
        } catch (IOException exception) {
            return "";
        }
    }

    private String currentVersion() {
        try {
            return FabricLoader.getInstance().getModContainer("micx-fabric")
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("?");
        } catch (Throwable ignored) {
            return "?";
        }
    }

    /** 正在跑的那个 jar 文件；开发环境（目录）或拿不到时返回 null。 */
    private Path ownJarPath() {
        try {
            URL location = UpdateModule.class.getProtectionDomain().getCodeSource().getLocation();
            if (location != null) {
                Path path;
                try {
                    path = Path.of(location.toURI());
                } catch (java.net.URISyntaxException spaced) {
                    // Knot 在含空格的安装路径上给出未编码的 file: URL（macOS「Application Support」），
                    // toURI() 直接抛异常 → 0.2.131 的「拿不到当前 jar 路径」。手动解码 path 段。
                    path = Path.of(java.net.URLDecoder.decode(
                            location.getPath(), java.nio.charset.StandardCharsets.UTF_8));
                }
                if (Files.isRegularFile(path)) return path;
            }
        } catch (Throwable ignored) {
        }
        // 兜底：mods/ 里恰好只有一份本 mod 的 jar 时，它就是换装对象（唯一候选不会认错）。
        try {
            List<Path> jars = UpdateInstaller.ownJars(
                    FabricLoader.getInstance().getGameDir().resolve("mods"));
            return jars.size() == 1 ? jars.get(0) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void fail(String message) {
        state = State.FAILED;
        detail = message;
    }

    private void queueChat(String message) {
        pendingChat = message;
    }

    private void announcePending(Minecraft client) {
        String message = pendingChat;
        if (message == null) return;
        // 还没进世界（标题界面）时不丢消息：留在队列里，进世界后的第一个 tick 再发
        if (client == null || client.player == null) return;
        pendingChat = null;
        client.player.sendSystemMessage(ChatMessageStyles.notice("MICx 更新：" + message));
    }

    /** 换装之后如果 mods/ 里还有第二份自己的 jar，说清楚要删哪个，而不是静默硬加载。 */
    private void warnAboutDuplicateJars(Minecraft client) {
        if (doubleJarWarned || state != State.INSTALLED) return;
        doubleJarWarned = true;
        Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
        List<Path> jars = UpdateInstaller.ownJars(modsDir);
        if (jars.size() < 2) return;
        StringBuilder names = new StringBuilder();
        for (Path jar : jars) {
            if (names.length() > 0) names.append("、");
            names.append(jar.getFileName());
        }
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(ChatMessageStyles.notice(
                    "MICx 更新：mods/ 里有两个本 mod 的 jar（" + names + "），请只保留一个再启动"));
        }
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        enabled = ModuleStateStore.get(id(), defaultEnabled());
        Properties properties = ConfigProperties.load(
                FabricRuntime.configPath().resolve("auto-update.properties"), null);
        String install = properties.getProperty("autoInstall");
        if (install != null) autoInstall = !"false".equalsIgnoreCase(install.trim());
        String token = properties.getProperty("tokenUrl");
        if (token != null) tokenUrl = token.trim();
        localVersion = currentVersion();
    }

    private void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("autoInstall", Boolean.toString(autoInstall));
        properties.setProperty("tokenUrl", tokenUrl);
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("auto-update.properties"),
                    properties, "MICx auto update");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save auto update configuration", exception);
        }
    }

    /** 给流套一层字节上限，超了直接断，避免被人塞一个超大响应。 */
    private static final class BoundedInputStream extends InputStream {
        private final HttpsURLConnection connection;
        private final InputStream delegate;
        private final long limit;
        private long read;

        BoundedInputStream(HttpsURLConnection connection, InputStream delegate, long limit) {
            this.connection = connection;
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0 && ++read > limit) throw new IOException("响应超过上限");
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = delegate.read(buffer, offset, length);
            if (count > 0 && (read += count) > limit) throw new IOException("响应超过上限");
            return count;
        }

        @Override
        public void close() throws IOException {
            try {
                delegate.close();
            } finally {
                connection.disconnect();
            }
        }
    }
}
