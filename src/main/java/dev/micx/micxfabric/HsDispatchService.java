package dev.micx.micxfabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Background HS presence/dispatch service. It never performs network work on the client thread. */
public final class HsDispatchService implements AutoCloseable {
    private static final HsDispatchService INSTANCE = new HsDispatchService();
    private static final int TIMEOUT_MS = 8_000;
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final long QUEUE_INTERVAL_MS = 5_000L;
    private static final long QUEUE_TIMEOUT_MS = 180_000L;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "MICx-Hs");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong generation = new AtomicLong();
    private final AtomicBoolean presenceInFlight = new AtomicBoolean();
    private final Object lifecycleLock = new Object();
    private HsConfig config;
    private TeamSyncTokenProvider tokenProvider;
    private ScheduledFuture<?> queueFuture;
    private volatile long lastPresenceMs;
    private volatile boolean closed;

    private HsDispatchService() {
    }

    public static HsDispatchService instance() {
        return INSTANCE;
    }

    public synchronized void initialize() {
        if (config != null || closed) return;
        config = new HsConfig();
        config.load();
        tokenProvider = new TeamSyncTokenProvider(config.tokenUrl);
        tokenProvider.refreshAsync();
    }

    public void tick(Minecraft client) {
        initialize();
        HsConfig current = config;
        if (closed || current == null || !current.enabled || client == null
                || client.player == null || client.level == null) return;
        long now = System.currentTimeMillis();
        if (now - lastPresenceMs < current.presenceIntervalSeconds * 1_000L
                || !presenceInFlight.compareAndSet(false, true)) return;
        String playerName = client.player.getName().getString();
        if (!isPlayerName(playerName)) {
            presenceInFlight.set(false);
            return;
        }
        lastPresenceMs = now;
        long requestGeneration = generation.get();
        executor.execute(() -> {
            try {
                String bearer = awaitBearer();
                if (bearer != null) postJson(current.presenceUrl,
                        "{\"player_name\":\"" + escapeJson(playerName) + "\"}", bearer);
            } finally {
                presenceInFlight.set(false);
            }
            if (requestGeneration != generation.get()) return;
        });
    }

    public void dispatch(FabricClientCommandSource source, String[] args) {
        initialize();
        if (source == null) return;
        int count = parseCount(args);
        if (count < 1 || count > 3) {
            source.sendFeedback(ChatMessageStyles.warning("usage: /micx hs <1-3>"));
            return;
        }
        Minecraft client = source.getClient();
        if (client == null || client.player == null || client.level == null) {
            source.sendFeedback(ChatMessageStyles.warning("HS requires an active world and player."));
            return;
        }
        HsConfig current = config;
        if (current == null || !current.enabled) {
            source.sendFeedback(ChatMessageStyles.warning("HS dispatch is disabled in hs.properties."));
            return;
        }
        String playerName = client.player.getName().getString();
        if (!isPlayerName(playerName)) {
            source.sendFeedback(ChatMessageStyles.error("Invalid Minecraft player name."));
            return;
        }
        long requestGeneration = generation.get();
        source.sendFeedback(ChatMessageStyles.feedback("正在调度 " + count + " 个 bot..."));
        executor.execute(() -> {
            DispatchResult result = requestDispatch(current, playerName, count);
            client.execute(() -> {
                if (requestGeneration != generation.get() || client.player == null || client.level == null) return;
                handleDispatchResult(client, playerName, count, result, requestGeneration);
            });
        });
    }

    public void resetTransientState() {
        generation.incrementAndGet();
        synchronized (lifecycleLock) {
            if (queueFuture != null) queueFuture.cancel(false);
            queueFuture = null;
        }
        lastPresenceMs = 0L;
        presenceInFlight.set(false);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        resetTransientState();
        TeamSyncTokenProvider provider = tokenProvider;
        tokenProvider = null;
        if (provider != null) provider.close();
        executor.shutdownNow();
    }

    static int parseCount(String[] args) {
        if (args == null || args.length != 2) return -1;
        try {
            int count = Integer.parseInt(args[1]);
            return count >= 1 && count <= 3 ? count : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    static boolean isPlayerName(String value) {
        return value != null && value.matches("[A-Za-z0-9_]{1,16}");
    }

    static DispatchResult parseDispatch(String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            return new DispatchResult(
                    json.has("ok") && json.get("ok").getAsBoolean(),
                    parseStrings(json, "available"), parseStrings(json, "queued"),
                    json.has("wait_est_seconds") && !json.get("wait_est_seconds").isJsonNull()
                            ? json.get("wait_est_seconds").getAsInt() : -1,
                    json.has("error") && !json.get("error").isJsonNull()
                            ? json.get("error").getAsString() : null);
        } catch (RuntimeException exception) {
            return new DispatchResult(false, List.of(), List.of(), -1, "invalid response");
        }
    }

    static QueueResult parseQueue(String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            return new QueueResult(parseStrings(json, "ready_bots"),
                    json.has("pending") && json.get("pending").isJsonArray()
                            ? json.getAsJsonArray("pending").size() : -1);
        } catch (RuntimeException exception) {
            return new QueueResult(List.of(), -1);
        }
    }

    private void handleDispatchResult(Minecraft client, String playerName, int requested,
                                       DispatchResult result, long requestGeneration) {
        if (!result.ok()) {
            client.player.sendSystemMessage(ChatMessageStyles.error("HS 调度失败："
                    + (result.error() == null ? "backend unavailable" : result.error())));
            return;
        }
        if (!result.available().isEmpty()) {
            String command = "p " + String.join(" ", result.available());
            client.player.connection.sendCommand(command);
        }
        if (!result.queued().isEmpty()) {
            int seconds = result.waitEstSeconds() < 0 ? 0 : result.waitEstSeconds();
            client.player.sendSystemMessage(ChatMessageStyles.feedback(
                    "已排队 " + result.queued().size() + " 个 bot" + (seconds > 0 ? "，预计 " + seconds + " 秒" : "")));
            startPolling(client, playerName, result.queued().size(), requestGeneration);
        } else if (result.available().isEmpty()) {
            client.player.sendSystemMessage(ChatMessageStyles.warning(
                    "HS 暂无可用 bot。"));
        }
    }

    private void startPolling(Minecraft client, String playerName, int queuedCount, long requestGeneration) {
        synchronized (lifecycleLock) {
            if (queueFuture != null) queueFuture.cancel(false);
            long startedAt = System.currentTimeMillis();
            Set<String> notified = new HashSet<>();
            queueFuture = executor.scheduleAtFixedRate(() -> {
                if (closed || requestGeneration != generation.get()
                        || System.currentTimeMillis() - startedAt >= QUEUE_TIMEOUT_MS
                        || notified.size() >= queuedCount) {
                    cancelCurrentQueue();
                    return;
                }
                QueueResult queue = requestQueue(config, playerName);
                for (String bot : queue.readyBots()) {
                    if (notified.size() >= queuedCount || !isPlayerName(bot) || !notified.add(bot)) continue;
                    client.execute(() -> {
                        if (requestGeneration == generation.get() && client.player != null && client.level != null) {
                            sendClickableInvite(client, bot);
                        }
                    });
                }
                if (queue.pendingCount() == 0 && !notified.isEmpty()) cancelCurrentQueue();
            }, QUEUE_INTERVAL_MS, QUEUE_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void cancelCurrentQueue() {
        synchronized (lifecycleLock) {
            if (queueFuture != null) {
                queueFuture.cancel(false);
                queueFuture = null;
            }
        }
    }

    private DispatchResult requestDispatch(HsConfig current, String playerName, int count) {
        String bearer = awaitBearer();
        if (bearer == null) return new DispatchResult(false, List.of(), List.of(), -1, "token unavailable");
        String endpoint = HsConfig.deriveEndpoint(current.jobUrl, "dispatch");
        String body = "{\"player_name\":\"" + escapeJson(playerName) + "\",\"count\":" + count + "}";
        String response = postJson(endpoint, body, bearer);
        return response == null ? new DispatchResult(false, List.of(), List.of(), -1, "connection failed")
                : parseDispatch(response);
    }

    private QueueResult requestQueue(HsConfig current, String playerName) {
        String bearer = awaitBearer();
        if (bearer == null) return new QueueResult(List.of(), -1);
        String endpoint = HsConfig.deriveEndpoint(current.jobUrl, "queue");
        try {
            URI uri = URI.create(endpoint + "?player=" + URLEncoder.encode(playerName, StandardCharsets.UTF_8));
            String body = request("GET", uri.toString(), null, bearer);
            return body == null ? new QueueResult(List.of(), -1) : parseQueue(body);
        } catch (RuntimeException exception) {
            return new QueueResult(List.of(), -1);
        }
    }

    private String postJson(String endpoint, String body, String bearer) {
        return request("POST", endpoint, body, bearer);
    }

    private String request(String method, String endpoint, String body, String bearer) {
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(endpoint);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getRawQuery() != null && body != null
                    || uri.getRawFragment() != null) return null;
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Authorization", "Bearer " + bearer);
            if (body != null) {
                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setFixedLengthStreamingMode(payload.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(payload);
                }
            }
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return null;
            return readBounded(connection.getInputStream());
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String awaitBearer() {
        TeamSyncTokenProvider provider = tokenProvider;
        if (provider == null) return null;
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (!closed && System.currentTimeMillis() < deadline) {
            JsonObject token = provider.getTokenNonBlocking();
            if (token != null && token.has("token") && !token.get("token").isJsonNull()) {
                String value = token.get("token").getAsString();
                if (!value.isBlank()) return value;
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private static String readBounded(InputStream stream) throws IOException {
        try (InputStream input = stream;
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[2048];
            int total = 0;
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                total += count;
                if (total > MAX_BODY_BYTES) throw new IOException("response too large");
                builder.append(buffer, 0, count);
            }
            return builder.toString();
        }
    }

    private static List<String> parseStrings(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) return List.of();
        List<String> values = new ArrayList<>();
        JsonArray array = json.getAsJsonArray(key);
        for (JsonElement element : array) {
            if (element.isJsonPrimitive()) values.add(element.getAsString());
        }
        return List.copyOf(values);
    }

    private static String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void sendClickableInvite(Minecraft client, String botName) {
        Component invite = Component.literal(" [点击邀请]").withStyle(Style.EMPTY.withColor(ChatMessageStyles.WARNING)
                .withClickEvent(new ClickEvent.RunCommand("/p " + botName))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("点击邀请 " + botName))));
        client.player.sendSystemMessage(ChatMessageStyles.feedback(botName + " 已就绪！").copy().append(invite));
    }

    public record DispatchResult(boolean ok, List<String> available, List<String> queued,
                                 int waitEstSeconds, String error) {
    }

    public record QueueResult(List<String> readyBots, int pendingCount) {
    }
}
