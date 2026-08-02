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
    private volatile ExecutorService executor = newExecutor();
    private final Set<Future<?>> tasks = ConcurrentHashMap.newKeySet();
    private volatile boolean enabled;
    private volatile boolean registered;
    private volatile Future<?> activeTask;
    private volatile int timeoutMs = 25_000;
    private volatile long generation;

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
            Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_ChatTranslate.cfg");
            if (!Files.isRegularFile(file) && Files.isRegularFile(legacy)) {
                Properties legacyProperties = new Properties();
                try (InputStream input = Files.newInputStream(legacy)) {
                    legacyProperties.load(input);
                }
                configuredTimeout = parseTimeout(legacyProperties.getProperty("timeoutMs"), configuredTimeout);
            }
            this.timeoutMs = clampTimeout(configuredTimeout);
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
        Minecraft client = Minecraft.getInstance();
        long taskGeneration = generation;
        int taskTimeoutMs = timeoutMs;
        ensureExecutor();
        final FutureTask<?>[] holder = new FutureTask<?>[1];
        FutureTask<Void> task = new FutureTask<>(() -> {
            try {
                translateAndSend(client, message, taskGeneration, taskTimeoutMs);
            } finally {
                FutureTask<?> current = holder[0];
                if (current != null) tasks.remove(current);
                if (activeTask == current) activeTask = null;
            }
            return null;
        });
        holder[0] = task;
        tasks.add(task);
        activeTask = task;
        try {
            executor.execute(task);
        } catch (RejectedExecutionException exception) {
            tasks.remove(task);
            activeTask = null;
            return true;
        }
        return false;
    }

    private void translateAndSend(Minecraft client, String source, long taskGeneration, int taskTimeoutMs) {
        String outbound;
        try {
            outbound = ChatTranslationClient.translate(source, taskTimeoutMs);
        } catch (ChatTranslationClient.TranslationException ignored) {
            client.execute(() -> {
                if (enabled && taskGeneration == generation && client.player != null) {
                    client.player.sendSystemMessage(Component.literal("[MICx Translate] 翻译失败，原文未发送。"));
                }
            });
            return;
        }
        String finalOutbound = outbound;
        client.execute(() -> {
            if (enabled && taskGeneration == generation && client.player != null && client.level != null) {
                client.player.connection.sendChat(finalOutbound);
            }
        });
    }

    public String apiKey() {
        return "";
    }

    public int timeoutMs() {
        return timeoutMs;
    }

    public void cancelTasks() {
        generation++;
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

    public boolean saveConfiguration(int timeoutMs) {
        int nextTimeout = clampTimeout(timeoutMs);
        Path file = FabricRuntime.configPath().resolve("chat-translate.properties");
        Properties properties = new Properties();
        properties.setProperty("timeout_ms", Integer.toString(nextTimeout));
        try {
            AtomicProperties.store(file, properties, "MICx Toolkit chat translation configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save chat translation configuration", exception);
            return false;
        }
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
