package dev.micx.micxfabric;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import dev.micx.micxfabric.render.HudRuntime;
import org.lwjgl.glfw.GLFW;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.util.Base64;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.io.IOException;
import java.util.Properties;

public final class AsrModule implements Module {
    private static final AsrModule INSTANCE = new AsrModule();
    private static final String ENDPOINT = "wss://api.stepfun.com/v1/realtime/asr/stream";
    private static final String BUILT_IN_KEY = "";
    private static final int SAMPLE_RATE = 16000;
    private static final int CHUNK_BYTES = 3200;
    private static final long MAX_RECORDING_MS = 30_000L;
    private static final long FINALIZING_TIMEOUT_MS = 5_000L;
    private static final long CONNECTING_TIMEOUT_MS = 8_000L;

    private final WebSocketFactory socketFactory = new WebSocketFactory();
    private final AtomicInteger eventIds = new AtomicInteger();
    private final Object stateLock = new Object();
    private volatile boolean enabled;
    private volatile State state = State.IDLE;
    private volatile String transcript = "";
    private volatile String stash = "";
    private volatile String apiKey = BUILT_IN_KEY;
    private volatile int pttKey = GLFW.GLFW_KEY_V;
    private volatile long generation;
    private volatile Session activeSession;
    private boolean previousPttDown;
    private boolean previousEnterDown;
    private boolean previousEscapeDown;

    private enum State { IDLE, CONNECTING, RECORDING, FINALIZING, PENDING }

    private static final class Session {
        private final long id;
        private volatile WebSocket socket;
        private volatile TargetDataLine microphone;
        private volatile Thread captureThread;
        private volatile long connectingStartedAt;
        private volatile long recordingStartedAt;
        private volatile long finalizingStartedAt;
        private volatile boolean commitRequested;
        private volatile boolean commitSent;
        private volatile boolean sessionReady;

        private Session(long id) {
            this.id = id;
        }
    }

    private AsrModule() {
    }

    public static AsrModule instance() {
        return INSTANCE;
    }

    public void initializeConfig() {
        Path file = FabricRuntime.configPath().resolve("asr.properties");
        Properties properties = new Properties();
        try {
            if (Files.isRegularFile(file)) {
                try (InputStream input = Files.newInputStream(file)) {
                    properties.load(input);
                }
            }
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to load ASR configuration", exception);
        }
        String configured = properties.getProperty("api_key");
        if (configured == null || configured.isBlank()) configured = System.getenv("MICX_STEP_API_KEY");
        if (configured == null || configured.isBlank()) configured = BUILT_IN_KEY;
        setApiKey(configured);
        String configuredPtt = properties.getProperty("ptt_key");
        if (configuredPtt != null) {
            try {
                pttKey = normalizeBinding(Integer.parseInt(configuredPtt));
            } catch (NumberFormatException ignored) {
                // Keep the default PTT key for malformed configuration.
            }
        }
        if (apiKey.isBlank()) {
            MicxFabric.LOGGER.warn("ASR is disabled until MICX_STEP_API_KEY or config/MICxToolkit/asr.properties api_key is configured");
        }
    }

    @Override
    public String id() {
        return "asr";
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) cancel();
        ModuleStateStore.put(id(), enabled);
    }

    @Override
    public void tick(Minecraft client) {
        if (client == null || client.getWindow() == null) return;
        boolean guiOpen = client.gui != null && client.gui.screen() != null;
        if (guiOpen) {
            if (state != State.IDLE) cancel();
            previousPttDown = false;
            previousEnterDown = false;
            previousEscapeDown = false;
            return;
        }
        boolean pttDown = new InputBinding(pttKey).down(client);
        boolean enterDown = GLFW.glfwGetKey(client.getWindow().handle(), GLFW.GLFW_KEY_ENTER) == GLFW.GLFW_PRESS;
        boolean escapeDown = GLFW.glfwGetKey(client.getWindow().handle(), GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS;

        if (enabled && pttDown && !previousPttDown) start();
        if (enabled && !pttDown && previousPttDown) commit();
        if (state == State.PENDING && enterDown && !previousEnterDown) sendPending(client);
        if (state == State.PENDING && escapeDown && !previousEscapeDown) cancel();
        Session session = activeSession;
        if (session != null) {
            long nowNanos = System.nanoTime();
            if (state == State.RECORDING && elapsedMillis(nowNanos, session.recordingStartedAt) >= MAX_RECORDING_MS) commit();
            if (state == State.CONNECTING && elapsedMillis(nowNanos, session.connectingStartedAt) >= CONNECTING_TIMEOUT_MS) cancel();
            if (state == State.FINALIZING && elapsedMillis(nowNanos, session.finalizingStartedAt) >= FINALIZING_TIMEOUT_MS) cancel();
        }

        previousPttDown = pttDown;
        previousEnterDown = enterDown;
        previousEscapeDown = escapeDown;
    }

    private void start() {
        Session session;
        synchronized (stateLock) {
            if (state != State.IDLE || apiKey.isBlank()) return;
            session = new Session(++generation);
            session.connectingStartedAt = System.nanoTime();
            activeSession = session;
            state = State.CONNECTING;
            transcript = "";
            stash = "";
        }
        try {
            WebSocket ws = socketFactory.createSocket(ENDPOINT)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addListener(new WebSocketAdapter() {
                        @Override
                        public void onConnected(WebSocket websocket, Map<String, List<String>> headers) {
                            if (!isCurrent(session, websocket)) return;
                            session.socket = websocket;
                            websocket.sendText(sessionUpdate());
                        }

                        @Override
                        public void onTextMessage(WebSocket websocket, String message) {
                            if (isCurrent(session, websocket)) handle(message, session, websocket);
                        }

                        @Override
                        public void onConnectError(WebSocket websocket, com.neovisionaries.ws.client.WebSocketException exception) {
                            if (isCurrent(session, websocket)) cancel(session);
                        }

                        @Override
                        public void onError(WebSocket websocket, com.neovisionaries.ws.client.WebSocketException exception) {
                            if (isCurrent(session, websocket)) cancel(session);
                        }

                        @Override
                        public void onDisconnected(WebSocket websocket, com.neovisionaries.ws.client.WebSocketFrame serverCloseFrame,
                                                   com.neovisionaries.ws.client.WebSocketFrame clientCloseFrame, boolean closedByServer) {
                            if (isCurrent(session, websocket) && state != State.PENDING) cancel(session);
                        }
                    });
            session.socket = ws;
            ws.connectAsynchronously();
        } catch (Exception exception) {
            cancel(session);
        }
    }

    private void startMicrophone(Session session, WebSocket websocket) {
        if (!isCurrent(session, websocket) || state != State.CONNECTING) return;
        TargetDataLine line = null;
        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();
            synchronized (stateLock) {
                if (!isCurrent(session, websocket) || state != State.CONNECTING) {
                    closeLine(line);
                    return;
                }
                session.microphone = line;
                session.recordingStartedAt = System.nanoTime();
                state = State.RECORDING;
                TargetDataLine activeLine = line;
                Thread thread = new Thread(() -> captureLoop(session, websocket, activeLine), "MICx-ASR-Audio");
                session.captureThread = thread;
                thread.setDaemon(true);
                thread.start();
            }
        } catch (LineUnavailableException | RuntimeException exception) {
            closeLine(line);
            cancel(session);
        }
    }

    private void captureLoop(Session session, WebSocket websocket, TargetDataLine line) {
        byte[] buffer = new byte[CHUNK_BYTES];
        try {
            while (isCurrent(session, websocket) && state == State.RECORDING && line.isOpen() && !Thread.currentThread().isInterrupted()) {
                int read = line.read(buffer, 0, buffer.length);
                if (read <= 0 || !isCurrent(session, websocket) || !websocket.isOpen()) continue;
                byte[] audio = read == buffer.length ? buffer.clone() : java.util.Arrays.copyOf(buffer, read);
                JsonObject event = event("input_audio_buffer.append");
                event.addProperty("audio", Base64.getEncoder().encodeToString(audio));
                websocket.sendText(event.toString());
            }
        } catch (RuntimeException exception) {
            MicxFabric.LOGGER.warn("ASR audio capture stopped", exception);
        }
    }

    private void commit() {
        Session session;
        synchronized (stateLock) {
            session = activeSession;
            if (session == null || (state != State.RECORDING && state != State.CONNECTING)) return;
            if (session.commitRequested) return;
            session.commitRequested = true;
            state = State.FINALIZING;
            session.finalizingStartedAt = System.nanoTime();
        }
        closeMicrophone(session);
        if (session.sessionReady) sendCommit(session);
    }

    private void sendCommit(Session session) {
        WebSocket ws = session.socket;
        synchronized (stateLock) {
            if (!isCurrent(session, ws) || session.commitSent || !session.commitRequested || ws == null || !ws.isOpen()) return;
            session.commitSent = true;
        }
        try {
            ws.sendText(event("input_audio_buffer.commit").toString());
        } catch (RuntimeException exception) {
            MicxFabric.LOGGER.warn("ASR commit failed", exception);
            session.commitSent = false;
            cancel(session);
        }
    }

    private void sendPending(Minecraft client) {
        String message = transcript.trim();
        if (message.isEmpty()) {
            cancel();
            return;
        }
        if (client.player != null && client.level != null) client.player.connection.sendChat(message);
        cancel();
    }

    private void cancel() {
        Session session;
        synchronized (stateLock) {
            session = activeSession;
        }
        if (session != null) cancel(session);
    }

    private void cancel(Session session) {
        WebSocket ws;
        synchronized (stateLock) {
            if (activeSession != session) return;
            activeSession = null;
            ++generation;
            state = State.IDLE;
            transcript = "";
            stash = "";
            ws = session.socket;
            session.socket = null;
        }
        closeMicrophone(session);
        if (ws != null) {
            try { ws.disconnect(); } catch (Throwable ignored) { }
        }
    }

    private void closeMicrophone(Session session) {
        TargetDataLine line = session.microphone;
        Thread thread = session.captureThread;
        session.microphone = null;
        session.captureThread = null;
        if (line != null) {
            closeLine(line);
        }
        if (thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
            try {
                thread.join(250L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void closeLine(TargetDataLine line) {
        try { line.stop(); } catch (Throwable ignored) { }
        try { line.close(); } catch (Throwable ignored) { }
    }

    private boolean isCurrent(Session session, WebSocket websocket) {
        return activeSession == session && session.socket == websocket && enabled;
    }

    private void handle(String raw, Session session, WebSocket websocket) {
        try {
            JsonObject root = new JsonParser().parse(raw).getAsJsonObject();
            String type = root.has("type") ? root.get("type").getAsString() : "";
            if ("session.updated".equals(type) && (state == State.CONNECTING || state == State.FINALIZING)) {
                session.sessionReady = true;
                if (state == State.CONNECTING) {
                    if (session.commitRequested) {
                        sendCommit(session);
                    } else {
                        startMicrophone(session, websocket);
                    }
                } else if (state == State.FINALIZING && session.commitRequested) {
                    sendCommit(session);
                }
                return;
            }
            if ("conversation.item.input_audio_transcription.delta".equals(type)) {
                transcript = correct(root.has("text") ? root.get("text").getAsString() : transcript);
                stash = correct(root.has("stash") ? root.get("stash").getAsString() : stash);
            }
            if ("conversation.item.input_audio_transcription.completed".equals(type)) {
                transcript = correct(root.has("transcript") ? root.get("transcript").getAsString() : transcript);
                if (transcript.isBlank()) {
                    cancel(session);
                    return;
                }
                state = State.PENDING;
                closeSocketAfterCompletion(session, websocket);
            }
            if ("error".equals(type)) cancel(session);
        } catch (RuntimeException exception) {
            MicxFabric.LOGGER.warn("ASR message processing failed", exception);
        }
    }

    private void closeSocketAfterCompletion(Session session, WebSocket websocket) {
        if (!isCurrent(session, websocket)) return;
        closeMicrophone(session);
        synchronized (stateLock) {
            if (activeSession != session || session.socket != websocket) return;
            session.socket = null;
        }
        try { websocket.disconnect(); } catch (Throwable ignored) { }
    }

    private static long elapsedMillis(long nowNanos, long startedNanos) {
        if (startedNanos <= 0L) return 0L;
        return (nowNanos - startedNanos) / 1_000_000L;
    }

    private String sessionUpdate() {
        JsonObject format = new JsonObject();
        format.addProperty("type", "pcm");
        format.addProperty("codec", "pcm_s16le");
        format.addProperty("rate", SAMPLE_RATE);
        format.addProperty("bits", 16);
        format.addProperty("channel", 1);
        JsonObject transcription = new JsonObject();
        transcription.addProperty("model", "stepaudio-2.5-asr-stream");
        transcription.addProperty("language", "zh");
        transcription.addProperty("full_rerun_on_commit", true);
        transcription.addProperty("enable_itn", true);
        transcription.addProperty("prompt", "Hypixel Zombies terms: AA, ult, LR, FR, EH, LS, eco, rr, CC, DSG, ZP, TOO. Keep English game terms as English.");
        JsonObject input = new JsonObject();
        input.add("format", format);
        input.add("transcription", transcription);
        JsonObject audio = new JsonObject();
        audio.add("input", input);
        JsonObject session = new JsonObject();
        session.add("audio", audio);
        JsonObject root = event("session.update");
        root.add("session", session);
        return root.toString();
    }

    private JsonObject event(String type) {
        JsonObject root = new JsonObject();
        root.addProperty("event_id", "micx_" + eventIds.incrementAndGet());
        root.addProperty("type", type);
        return root;
    }

    public void drawHud(GuiGraphicsExtractor graphics) {
        if (!enabled || state == State.IDLE) return;
        int y = graphics.guiHeight() - 64;
        String text = transcript.isBlank() ? "识别中…" : transcript;
        graphics.text(Minecraft.getInstance().font, Component.literal(text), 8, y, 0xFFFFFFFF, true);
        if (!stash.isBlank()) graphics.text(Minecraft.getInstance().font, Component.literal(stash), 8, y + 12, 0xFF888888, true);
        if (state == State.PENDING) graphics.text(Minecraft.getInstance().font, Component.literal("[Enter] 发送  [Esc] 取消"), 8, y + 24, 0xFFB8B8B8, true);
    }

    private static String correct(String text) {
        if (text == null) return "";
        return text.replace("out", "ult").replace("医疗厅", "ult").replace("医务室", "ult")
                .replace("aa", "AA").replace("Okay", "AA");
    }

    public String apiKey() {
        return apiKey;
    }

    public int pttKey() {
        return pttKey;
    }

    public void setPttKey(int key) {
        pttKey = normalizeBinding(key);
        previousPttDown = false;
        saveConfiguration();
    }

    public void cancelSession() {
        cancel();
        previousPttDown = false;
        previousEnterDown = false;
        previousEscapeDown = false;
    }

    @Override
    public void resetState() {
        cancelSession();
    }

    private static int normalizeBinding(int key) {
        if (key == 0) return GLFW.GLFW_KEY_UNKNOWN;
        return Math.max(-108, Math.min(GLFW.GLFW_KEY_LAST, key));
    }

    public boolean saveConfiguration() {
        return writeConfiguration(apiKey, pttKey);
    }

    public boolean saveConfiguration(String nextApiKey, int nextPttKey) {
        int normalizedPttKey = normalizeBinding(nextPttKey);
        String normalizedKey = nextApiKey == null ? "" : nextApiKey.trim();
        if (!writeConfiguration(normalizedKey, normalizedPttKey)) return false;
        cancelSession();
        apiKey = normalizedKey;
        pttKey = normalizedPttKey;
        return true;
    }

    private boolean writeConfiguration(String configuredApiKey, int configuredPttKey) {
        Path file = FabricRuntime.configPath().resolve("asr.properties");
        Properties properties = new Properties();
        if (configuredApiKey != null && !configuredApiKey.isBlank()) {
            properties.setProperty("api_key", configuredApiKey);
        }
        properties.setProperty("ptt_key", Integer.toString(configuredPttKey));
        try {
            AtomicProperties.store(file, properties, "MICx Toolkit ASR configuration");
            return true;
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save ASR configuration", exception);
            return false;
        }
    }

    public void setApiKey(String apiKey) {
        cancel();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public boolean consumeEscape() {
        if (!enabled || state != State.PENDING) return false;
        cancel();
        return true;
    }
}
