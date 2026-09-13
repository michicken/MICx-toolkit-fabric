package dev.micx.micxfabric;

import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;

public final class ChatTranslateModule implements Module {
    private static final ChatTranslateModule INSTANCE = new ChatTranslateModule();
    private static final int MAX_QUEUED = 3;
    private volatile ExecutorService executor = newExecutor();
    private final Set<Future<?>> tasks = ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.atomic.AtomicInteger queued = new java.util.concurrent.atomic.AtomicInteger();
    private volatile boolean enabled;
    private volatile boolean registered;
    private volatile Future<?> activeTask;
    private volatile int timeoutMs = 25_000;
    private volatile long generation;
    private volatile String status = "Idle";
    private volatile long lastLatencyMs;
    private volatile String apiKey = "";
    private volatile TranslationLanguage outgoingTargetLanguage = TranslationLanguage.EN;

    private static ExecutorService newExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "MICx-ChatTranslate");
            thread.setDaemon(true);
            return thread;
        });
    }

    private synchronized void ensureExecutor() {
        if (executor.isShutdown() || executor.isTerminated()) executor = newExecutor();
    }

    private ChatTranslateModule() {
    }

    public static ChatTranslateModule instance() {
        return INSTANCE;
    }

    public void initializeConfig() {
        Path file = FabricRuntime.configPath().resolve("chat-translate.properties");
        Properties properties = new Properties();
        try {
            Files.createDirectories(file.getParent());
            if (Files.isRegularFile(file)) {
                try (InputStream input = Files.newInputStream(file)) {
                    properties.load(input);
                }
            }
            int configuredTimeout = parseTimeout(properties.getProperty("timeout_ms"), 25_000);
            String configuredKey = properties.getProperty("api_key");
            String configuredTarget = properties.getProperty("outgoing_target_language");
            Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_ChatTranslate.cfg");
            if (!Files.isRegularFile(file) && Files.isRegularFile(legacy)) {
                Properties legacyProperties = new Properties();
                try (InputStream input = Files.newInputStream(legacy)) {
                    legacyProperties.load(input);
                }
                configuredTimeout = parseTimeout(legacyProperties.getProperty("timeoutMs"), configuredTimeout);
                String legacyKey = legacyProperties.getProperty("apiKey");
                if (legacyKey != null && !legacyKey.isBlank()) configuredKey = legacyKey.trim();
                String legacyTarget = legacyProperties.getProperty("outgoingTargetLanguage");
                if (legacyTarget != null && !legacyTarget.isBlank()) configuredTarget = legacyTarget.trim();
            }
            this.timeoutMs = clampTimeout(configuredTimeout);
            this.apiKey = configuredKey == null ? "" : configuredKey.trim();
            TranslationLanguage target = TranslationLanguage.fromCode(configuredTarget);
            this.outgoingTargetLanguage = (target == TranslationLanguage.AUTO || target == TranslationLanguage.ZH)
                    ? TranslationLanguage.EN : target;
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to load chat translation configuration", exception);
        }
    }


    @Override
    public String id() {
        return "chat_translate";
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) cancelTasks();
        if (enabled) {
            ensureExecutor();
            if (!registered) {
                ClientSendMessageEvents.ALLOW_CHAT.register(this::allowChatMessage);
                registered = true;
            }
        }
        ModuleStateStore.put(id(), enabled);
    }

    private boolean allowChatMessage(String message) {
        if (!enabled || !ChatTranslationRules.shouldTranslate(message)) return true;

        if (queued.get() >= MAX_QUEUED) {
            status = "Queue full";
            notice(ChatMessageStyles.error("Translate: 队列已满，原文未发送。"));
            return false;
        }
        ExecutorService target = executor;
        if (target == null || target.isShutdown()) {
            ensureExecutor();
            target = executor;
        }
        if (target == null || target.isShutdown()) {
            status = "Queue unavailable";
            notice(ChatMessageStyles.error("Translate: 队列不可用，原文未发送。"));
            return false;
        }
        String key = ChatTranslationClient.resolveKey(apiKey);
        if (key.isEmpty()) {
            status = "Missing API key";
            notice(ChatMessageStyles.error("Translate: 未配置 API key，请在面板填写，原文未发送。"));
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        long taskGeneration = generation;
        int taskTimeoutMs = timeoutMs;
        String taskKey = key;
        TranslationLanguage taskTarget = outgoingTargetLanguage;
        queued.incrementAndGet();
        status = "Translating";
        final ExecutorService queue = target;
        final FutureTask<?>[] holder = new FutureTask<?>[1];
        FutureTask<Void> task = new FutureTask<>(() -> {
            try {
                translateAndSend(client, message, taskGeneration, taskTimeoutMs, taskKey, taskTarget);
            } finally {
                FutureTask<?> current = holder[0];
                if (current != null) tasks.remove(current);
                if (activeTask == current) activeTask = null;
                queued.updateAndGet(value -> Math.max(0, value - 1));
            }
            return null;
        });
        holder[0] = task;
        tasks.add(task);
        activeTask = task;
        try {
            queue.execute(task);
        } catch (RejectedExecutionException exception) {
            tasks.remove(task);
            activeTask = null;
            queued.decrementAndGet();
            status = "Queue unavailable";
            notice(ChatMessageStyles.error("Translate: 队列不可用，原文未发送。"));
            return false;
        }
        return false;
    }

    private void translateAndSend(Minecraft client, String source, long taskGeneration, int taskTimeoutMs,
                                  String taskKey, TranslationLanguage taskTarget) {
        String outbound;
        long started = System.currentTimeMillis();
        try {
            outbound = ChatTranslationClient.translateOutbound(source, taskKey, taskTimeoutMs, taskTarget);
        } catch (ChatTranslationClient.TranslationException exception) {
            lastLatencyMs = Math.max(0L, System.currentTimeMillis() - started);
            String reason = displayFailure(exception.getMessage());
            status = reason;
            client.execute(() -> {
                if (enabled && taskGeneration == generation && client.player != null) {
                    notice(ChatMessageStyles.error("Translate: 翻译失败（" + reason + "），原文未发送。"));
                }
            });
            return;
        }
        lastLatencyMs = Math.max(0L, System.currentTimeMillis() - started);
        String finalOutbound = outbound;
        client.execute(() -> {
            if (enabled && taskGeneration == generation && client.player != null && client.level != null) {
                client.player.connection.sendChat(finalOutbound);
                status = "Sent";
            }
        });
    }

    /** 面板状态行（对照 1.8.9 statusLine）。 */
    public String statusLine() {
        int waiting = queued.get();
        String base = enabled ? status : "Off";
        String suffix = lastLatencyMs > 0 ? " · " + lastLatencyMs + "ms" : "";
        return base + (waiting > 0 ? " · queue " + waiting : "") + suffix;
    }

    public String apiKey() {
        return apiKey;
    }

    public TranslationLanguage outgoingTargetLanguage() {
        return outgoingTargetLanguage;
    }

    public void setOutgoingTargetLanguage(TranslationLanguage target) {
        if (target == null || target == TranslationLanguage.AUTO || target == TranslationLanguage.ZH) {
            this.outgoingTargetLanguage = TranslationLanguage.EN;
        } else {
            this.outgoingTargetLanguage = target;
        }
    }

    private void notice(net.minecraft.network.chat.Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        client.player.sendSystemMessage(message);
    }

    static String displayFailure(String reason) {
        if (reason == null) return "网络错误";
        return switch (reason) {
            case "timeout" -> "超时";
            case "missing_api_key" -> "未配置 API key";
            case "invalid_response", "empty_content", "missing_content", "invalid_json" -> "返回内容无效";
            case "response_too_large" -> "返回过长";
            default -> reason.startsWith("http_") ? reason : "网络错误";
        };
    }

    public int timeoutMs() {
        return timeoutMs;
    }

    public void cancelTasks() {
        generation++;
        status = enabled ? "Ready" : "Off";
        for (Future<?> task : tasks) task.cancel(true);
        tasks.clear();
        Future<?> task = activeTask;
        activeTask = null;
        if (task != null) task.cancel(true);
        ChatTranslationClient.cancelActiveRequests();
    }

    @Override
    public void resetState() {
        cancelTasks();
    }

    public void shutdown() {
        cancelTasks();
        executor.shutdownNow();
    }

    public boolean saveConfiguration(String apiKey, TranslationLanguage target, int timeoutMs) {
        int nextTimeout = clampTimeout(timeoutMs);
        String nextKey = apiKey == null ? "" : apiKey.trim();
        setOutgoingTargetLanguage(target);
        Path file = FabricRuntime.configPath().resolve("chat-translate.properties");
        Properties properties = new Properties();
        properties.setProperty("api_key", nextKey);
        properties.setProperty("outgoing_target_language", outgoingTargetLanguage.code());
        properties.setProperty("timeout_ms", Integer.toString(nextTimeout));
        try {
            AtomicProperties.store(file, properties, "MICx Toolkit chat translation configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save chat translation configuration", exception);
            return false;
        }
        this.apiKey = nextKey;
        this.timeoutMs = nextTimeout;
        cancelTasks();
        return true;
    }

    private static int clampTimeout(int timeoutMs) {
        return Math.max(5_000, Math.min(timeoutMs, 60_000));
    }

    private static int parseTimeout(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
