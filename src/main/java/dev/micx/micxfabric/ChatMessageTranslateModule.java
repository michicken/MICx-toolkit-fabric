package dev.micx.micxfabric;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import dev.micx.micxfabric.mixin.HudChatAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/** 聊天行尾 [T] 点击翻译成本地简体中文显示（对照 1.8.9 ChatMessageTranslateModule /micxt）。 */
public final class ChatMessageTranslateModule implements Module {
    private static final ChatMessageTranslateModule INSTANCE = new ChatMessageTranslateModule();
    private static final int MAX_QUEUED = 3;
    private static final int MAX_TRACKED = 128;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "MICx-ChatMessageTranslate");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<Integer, String> lineTexts = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, String> eldest) {
            return size() > MAX_TRACKED;
        }
    };
    private final AtomicInteger queued = new AtomicInteger();
    private final AtomicInteger lineIds = new AtomicInteger();
    private volatile boolean enabled = true;
    private volatile boolean registered;
    private volatile long generation;

    private ChatMessageTranslateModule() {
    }

    public static ChatMessageTranslateModule instance() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return "chat_message_translate";
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public void setEnabled(boolean value) {
        if (enabled == value && registered == value) return;
        enabled = value;
        generation++;
        if (value && !registered) {
            ClientReceiveMessageEvents.MODIFY_GAME.register(this::decorate);
            registered = true;
        }
        if (!value) {
            queued.set(0);
            synchronized (lineTexts) {
                lineTexts.clear();
            }
            ChatTranslationClient.cancelActiveRequests();
        }
        ModuleStateStore.put(id(), enabled);
    }

    /** 给每条收到的聊天行尾追加可点击 [T]（对照 1.8.9 LineDecorator.decorate）。 */
    private Component decorate(Component message, boolean overlay) {
        if (!enabled || overlay || message == null) return message;
        String plain = ChatTranslationRules.stripCleanerCountSuffix(message.getString());
        if (!ChatTranslationRules.shouldTranslateDisplayedMessage(plain)) return message;
        int id;
        synchronized (lineTexts) {
            id = lineIds.incrementAndGet();
            lineTexts.put(id, plain);
        }
        return message.copy().append(Component.literal("  [T]").withStyle(Style.EMPTY
                .withColor(ChatMessageStyles.MUTED)
                .withClickEvent(new ClickEvent.RunCommand("/micxt " + id))));
    }

    /** /micxt <lineId> 入口（FabricClientCommandSource 回调）。 */
    public void translateLine(FabricClientCommandSource source, int lineId) {
        if (!enabled) {
            source.sendFeedback(ChatMessageStyles.error("ChatMessageTranslate OFF. /micx toggle chat_message_translate first"));
            return;
        }
        String plain;
        synchronized (lineTexts) {
            plain = lineTexts.get(lineId);
        }
        if (plain == null) {
            source.sendFeedback(ChatMessageStyles.error("Translate: 该聊天行已过期，请重新点击新的 [T]。"));
            return;
        }
        requestTranslation(plain);
    }

    private void requestTranslation(String source) {
        if (!ChatTranslationRules.shouldTranslateDisplayedMessage(source)) {
            notice(ChatMessageStyles.warning("Translate: 没有可翻译的聊天文本。"));
            return;
        }
        if (queued.get() >= MAX_QUEUED) {
            notice(ChatMessageStyles.error("Translate: 翻译队列已满。"));
            return;
        }
        String key = ChatTranslationClient.resolveKey(ChatTranslateModule.instance().apiKey());
        if (key.isEmpty()) {
            notice(ChatMessageStyles.error("Translate: 未配置 API key，请在 ChatTranslate 面板填写。"));
            return;
        }
        Minecraft client = Minecraft.getInstance();
        long taskGeneration = generation;
        int taskTimeoutMs = ChatTranslateModule.instance().timeoutMs();
        queued.incrementAndGet();
        try {
            executor.execute(() -> {
                String translated = null;
                String failure = null;
                try {
                    translated = ChatTranslationClient.translateToChinese(source, key, taskTimeoutMs);
                } catch (ChatTranslationClient.TranslationException exception) {
                    failure = exception.getMessage();
                } finally {
                    queued.updateAndGet(value -> Math.max(0, value - 1));
                }
                String result = translated;
                String reason = failure;
                client.execute(() -> completeOnClient(client, result, reason, taskGeneration));
            });
        } catch (RejectedExecutionException ignored) {
            queued.decrementAndGet();
            notice(ChatMessageStyles.error("Translate: 翻译服务不可用。"));
        }
    }

    private void completeOnClient(Minecraft client, String translated, String failure, long taskGeneration) {
        if (!enabled || taskGeneration != generation) return;
        if (client.level == null) return;
        if (failure != null || translated == null) {
            String reason = ChatTranslateModule.displayFailure(failure == null ? "unknown" : failure);
            notice(ChatMessageStyles.error("Translate: 翻译失败（" + reason + "）。"));
            return;
        }
        if (client.gui != null && client.gui.hud != null) {
            ChatComponent chat = ((HudChatAccess) client.gui.hud).micx$chat();
            if (chat != null) {
                chat.addClientSystemMessage(Component.literal("[T] ")
                        .withStyle(Style.EMPTY.withColor(ChatMessageStyles.MUTED))
                        .append(Component.literal(translated).withStyle(Style.EMPTY.withColor(ChatMessageStyles.INFO))));
            }
        }
    }

    private void notice(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        client.player.sendSystemMessage(message);
    }

    @Override
    public void tick(Minecraft client) {
        if (client == null || client.level == null) {
            synchronized (lineTexts) {
                lineTexts.clear();
            }
        }
    }

    @Override
    public void resetState() {
        generation++;
        queued.set(0);
        synchronized (lineTexts) {
            lineTexts.clear();
        }
    }
}
