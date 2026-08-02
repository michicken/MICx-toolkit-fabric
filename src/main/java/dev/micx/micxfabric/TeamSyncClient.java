package dev.micx.micxfabric;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

/** Owned, epoch-guarded TeamSync WebSocket transport. */
public final class TeamSyncClient implements AutoCloseable {
    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int INBOUND_CAP = 512;

    private final Gson gson = new Gson();
    private final WebSocketFactory factory = new WebSocketFactory()
            .setConnectionTimeout(CONNECT_TIMEOUT_MS)
            .setVerifyHostname(true);
    private final AtomicReference<WebSocket> socket = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean();
    private final AtomicBoolean reconnected = new AtomicBoolean();
    private final AtomicLong epoch = new AtomicLong();
    private final AtomicInteger reconnectDelay = new AtomicInteger(1);
    private final ConcurrentLinkedQueue<Inbound> inbound = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "MICx-TeamSync");
        thread.setDaemon(true);
        return thread;
    });

    private volatile TeamSyncTokenProvider tokenProvider;
    private volatile ScheduledFuture<?> reconnectTask;
    private volatile URI endpoint;

    public synchronized void start(String url, TeamSyncTokenProvider provider) {
        stopConnectionOnly();
        TeamSyncTokenProvider previousProvider = tokenProvider;
        URI parsed = parseEndpoint(url);
        if (parsed == null) {
            epoch.incrementAndGet();
            running.set(false);
            endpoint = null;
            tokenProvider = null;
            clearInbound();
            if (previousProvider != null && previousProvider != provider) previousProvider.close();
            if (provider != null && provider != previousProvider) provider.close();
            return;
        }
        endpoint = parsed;
        if (previousProvider != null && previousProvider != provider) previousProvider.close();
        tokenProvider = provider;
        long currentEpoch = epoch.incrementAndGet();
        running.set(true);
        reconnectDelay.set(1);
        reconnected.set(false);
        clearInbound();
        if (provider != null) provider.refreshAsync();
        configureFactory(parsed);
        connect(currentEpoch);
    }

    public synchronized void stop() {
        epoch.incrementAndGet();
        running.set(false);
        reconnected.set(false);
        stopConnectionOnly();
        TeamSyncTokenProvider provider = tokenProvider;
        tokenProvider = null;
        if (provider != null) provider.close();
        endpoint = null;
        clearInbound();
    }

    private void stopConnectionOnly() {
        reconnectScheduled.set(false);
        ScheduledFuture<?> task = reconnectTask;
        if (task != null) task.cancel(false);
        reconnectTask = null;
        WebSocket current = socket.getAndSet(null);
        if (current != null) {
            try {
                current.disconnect();
            } catch (RuntimeException ignored) {
            }
        }
    }

    public boolean isConnected() {
        WebSocket current = socket.get();
        return running.get() && current != null && current.isOpen();
    }

    public boolean consumeReconnected() {
        return reconnected.getAndSet(false);
    }

    public String poll() {
        Inbound message;
        long currentEpoch = epoch.get();
        while ((message = inbound.poll()) != null) {
            if (running.get() && message.epoch == currentEpoch) return message.text;
        }
        return null;
    }

    private void connect(long currentEpoch) {
        if (!running.get() || epoch.get() != currentEpoch || endpoint == null) return;
        try {
            WebSocket created = factory.createSocket(endpoint.toString());
            created.setMaxPayloadSize(256 * 1024);
            created.addListener(new WebSocketAdapter() {
                @Override
                public void onConnected(WebSocket websocket, Map<String, List<String>> headers) {
                    if (!isCurrent(websocket, currentEpoch)) {
                        websocket.disconnect();
                        return;
                    }
                    reconnectDelay.set(1);
                    reconnected.set(true);
                }

                @Override
                public void onDisconnected(WebSocket websocket, com.neovisionaries.ws.client.WebSocketFrame serverCloseFrame,
                                            com.neovisionaries.ws.client.WebSocketFrame clientCloseFrame,
                                            boolean closedByServer) {
                    if (socket.compareAndSet(websocket, null)) scheduleReconnect(currentEpoch);
                }

                @Override
                public void onConnectError(WebSocket websocket, WebSocketException cause) {
                    if (socket.compareAndSet(websocket, null)) scheduleReconnect(currentEpoch);
                }

                @Override
                public void onTextMessage(WebSocket websocket, String text) {
                    if (!isCurrent(websocket, currentEpoch) || text == null) return;
                    inbound.offer(new Inbound(currentEpoch, text));
                    while (inbound.size() > INBOUND_CAP) inbound.poll();
                }
            });
            if (!running.get() || epoch.get() != currentEpoch || !socket.compareAndSet(null, created)) {
                created.disconnect();
                return;
            }
            created.connectAsynchronously();
        } catch (Exception ignored) {
            scheduleReconnect(currentEpoch);
        }
    }

    private boolean isCurrent(WebSocket candidate, long currentEpoch) {
        return running.get() && epoch.get() == currentEpoch && socket.get() == candidate;
    }

    private void scheduleReconnect(long currentEpoch) {
        if (!running.get() || epoch.get() != currentEpoch || !reconnectScheduled.compareAndSet(false, true)) return;
        int delay = reconnectDelay.getAndUpdate(value -> Math.min(value * 2, 30));
        reconnectTask = executor.schedule(() -> {
            reconnectScheduled.set(false);
            if (running.get() && epoch.get() == currentEpoch) connect(currentEpoch);
        }, delay, TimeUnit.SECONDS);
    }

    private void configureFactory(URI uri) {
        factory.setServerName(uri.getHost());
    }

    private static URI parseEndpoint(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            if (!"wss".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getPath() == null || uri.getPath().isBlank()) return null;
            if (uri.getPort() != -1 && uri.getPort() != 443) return null;
            return uri;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public boolean sendJoin(String name, java.util.Collection<String> roster, String map, int round) {
        JsonObject token = tokenProvider == null ? null : tokenProvider.getTokenNonBlocking();
        if (token == null) return false;
        StringBuilder json = new StringBuilder("{\"type\":\"join\",\"token\":")
                .append(gson.toJson(token)).append(",\"self\":").append(gson.toJson(safe(name)))
                .append(",\"roster\":[");
        boolean first = true;
        for (String player : roster == null ? List.<String>of() : roster) {
            if (!first) json.append(',');
            json.append(gson.toJson(safe(player)));
            first = false;
        }
        json.append("],\"map\":").append(gson.toJson(safe(map)))
                .append(",\"round\":").append(Math.max(0, round)).append('}');
        return send(json.toString());
    }

    public boolean sendState(String name, double x, double y, double z, float yaw, float pitch,
                             float hp, float maxHp, float absorption, String status,
                             int targetId, String targetType, double tx, double ty, double tz,
                             float targetHp, String targetName, int ping, long sequence) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "state");
        json.addProperty("name", safe(name));
        json.addProperty("state_seq", sequence);
        JsonObject position = new JsonObject();
        position.addProperty("x", finite(x));
        position.addProperty("y", finite(y));
        position.addProperty("z", finite(z));
        position.addProperty("yaw", finite(yaw));
        position.addProperty("pitch", finite(pitch));
        json.add("pos", position);
        json.addProperty("hp", finite(hp));
        json.addProperty("max", finite(maxHp));
        json.addProperty("abs", finite(absorption));
        json.addProperty("status", safe(status));
        if (targetId != Integer.MIN_VALUE) {
            JsonObject target = new JsonObject();
            target.addProperty("id", targetId);
            target.addProperty("type", safe(targetType));
            target.addProperty("x", finite(tx));
            target.addProperty("y", finite(ty));
            target.addProperty("z", finite(tz));
            target.addProperty("hp", finite(targetHp));
            target.addProperty("name", safe(targetName));
            json.add("target", target);
        }
        json.addProperty("ping", Math.max(0, ping));
        return send(gson.toJson(json));
    }

    public boolean sendPing(String name, double x, double y, double z, String label) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "ping");
        json.addProperty("name", safe(name));
        json.addProperty("x", finite(x));
        json.addProperty("y", finite(y));
        json.addProperty("z", finite(z));
        json.addProperty("label", safe(label));
        return send(gson.toJson(json));
    }

    public boolean sendLeave() { return send("{\"type\":\"leave\"}"); }
    public boolean sendHeartbeat() { return send("{\"type\":\"heartbeat\"}"); }

    private boolean send(String message) {
        WebSocket current = socket.get();
        if (!isConnected() || message == null || message.length() > 256 * 1024) return false;
        try {
            current.sendText(message);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void clearInbound() {
        inbound.clear();
    }

    private static String safe(String value) {
        if (value == null) return "";
        String result = value.replaceAll("[\\p{Cntrl}]", "").trim();
        return result.length() > 64 ? result.substring(0, 64) : result;
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static float finite(float value) {
        return Float.isFinite(value) ? value : 0.0f;
    }

    @Override
    public void close() {
        stop();
        executor.shutdownNow();
    }

    private record Inbound(long epoch, String text) {
    }
}
