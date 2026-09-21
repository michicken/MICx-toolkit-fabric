package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Chat translation configuration; DeepSeek API key is user-supplied (对照 1.8.9). */
public final class ChatTranslateConfigScreen extends ModuleConfigScreen {
    private EditBox timeoutBox;
    private EditBox apiKeyBox;
    private int toggleY;
    private int langY;

    public ChatTranslateConfigScreen(Screen parent) {
        super(parent, UiText.shown("聊天翻译", "ChatTranslate"), UiText.shown("ChatTranslate · DeepSeek", "聊天翻译 · DeepSeek"));
    }

    @Override
    protected void rebuildWidgets() {
        timeoutBox = new EditBox(font, 0, 0, 80, 20, Component.literal("timeout"));
        timeoutBox.setMaxLength(5);
        timeoutBox.setValue(Integer.toString(ChatTranslateModule.instance().timeoutMs()));
        timeoutBox.setBordered(true);
        addRenderableWidget(timeoutBox);
        apiKeyBox = new EditBox(font, 0, 0, 174, 20, Component.literal("api key"));
        apiKeyBox.setMaxLength(200);
        apiKeyBox.setValue(ChatTranslateModule.instance().apiKey());
        apiKeyBox.setBordered(true);
        addRenderableWidget(apiKeyBox);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        section(graphics, UiText.shown("开关与状态", "STATUS / 状态"), y);
        y += 20;
        graphics.text(font, UiText.shown("启用模块", "Enable"), contentLeft(), y + 4, TEXT);
        graphics.text(font, "中文普通聊天在后台翻译后再发送。", contentLeft(), y + 17, TEXT_DIM);
        toggleY = y + 1;
        int toggleX = contentRight() - 44;
        drawToggle(graphics, toggleX, toggleY, 44, 16, ChatTranslateModule.instance().enabled(),
                isInside(mouseX, mouseY, toggleX, toggleY, 44, 16));
        y += 38;
        graphics.text(font, UiText.shown("状态：", "Status: ") + ChatTranslateModule.instance().statusLine(),
                contentLeft(), y + 4, TEXT_DIM);
        y += 20;
        y = line(graphics, y);
        section(graphics, UiText.shown("翻译服务", "PROVIDER / 翻译服务"), y);
        y += 20;
        y = wrapped(graphics, "DeepSeek deepseek-v4-flash · Endpoint: https://api.deepseek.com/chat/completions",
                contentLeft(), y, TEXT_DIM, contentWidth());
        y += 14;
        y = line(graphics, y);
        section(graphics, UiText.shown("API 密钥", "API KEY / 密钥"), y);
        y += 20;
        graphics.text(font, UiText.shown("API 密钥", "API key"), contentLeft(), y + 4, TEXT);
        apiKeyBox.setX(contentRight() - 174);
        apiKeyBox.setY(y);
        y += 32;
        y = wrapped(graphics, UiText.shown("在这里填 DeepSeek 的 API key，只存在本地配置文件里。留空时会去读环境变量 MICX_DEEPSEEK_API_KEY。", "在此填写 DeepSeek API key（仅保存在本地配置文件）。留空时回退环境变量 MICX_DEEPSEEK_API_KEY。"),
                contentLeft(), y, TEXT_DIM, contentWidth());
        y += 14;
        y = line(graphics, y);
        section(graphics, UiText.shown("出站翻译目标语言", "TARGET LANGUAGE / 出站目标语言"), y);
        y += 20;
        graphics.text(font, UiText.shown("出站目标语言", "Outgoing target"), contentLeft(), y + 4, TEXT);
        langY = y;
        int langX = contentRight() - 174;
        drawButton(graphics, languageLabel(), langX, y, 174, 18,
                isInside(mouseX, mouseY, langX, y, 174, 18));
        y += 26;
        y = wrapped(graphics, UiText.shown("点按钮循环切换要翻成的目标语言（和 1.8.9 同一份列表）。只有中文的普通聊天会被拦下来翻译。", "点击按钮循环切换出站语言（1.8.9 同款列表）；出站只拦截中文普通消息。"),
                contentLeft(), y, TEXT_DIM, contentWidth());
        y += 14;
        y = line(graphics, y);
        section(graphics, UiText.shown("发送限制", "DELIVERY / 发送边界"), y);
        y += 20;
        graphics.text(font, UiText.shown("超时时间 ms", "Timeout (ms)"), contentLeft(), y + 4, TEXT);
        timeoutBox.setX(contentRight() - 80);
        timeoutBox.setY(y);
        y += 32;
        y = wrapped(graphics, UiText.shown("超时范围和原 1.8.9 一致：5000–60000 毫秒，默认 25000。", "范围沿用原 1.8.9：5000–60000 ms，默认 25000 ms。"),
                contentLeft(), y, TEXT_DIM, contentWidth());
        y += 14;
        y = line(graphics, y);
        y = wrapped(graphics, UiText.shown("只处理中文普通消息；/ 开头的命令、纯英文和超长文本都不会被拦。收到的外语消息：点聊天行尾的 [T]，在本地翻成简体中文显示。", "只处理中文普通消息；/ 命令、纯英文和超长文本不拦截。入站翻译：点击聊天行尾 [T] 本地翻译成简体中文显示。"),
                contentLeft(), y, TEXT_FAINT, contentWidth());
        setContentHeight(y - contentTop() + scrollOffset());
    }

    private int line(GuiGraphicsExtractor graphics, int y) {
        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        return y + 14;
    }

    private static String languageLabel() {
        TranslationLanguage language = ChatTranslateModule.instance().outgoingTargetLanguage();
        if (language == TranslationLanguage.AUTO) return "自动识别 Auto";
        return language.zhName() + " " + language.promptName();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            if (isInside(event.x(), event.y(), contentRight() - 44, toggleY, 44, 16)) {
                ChatTranslateModule module = ChatTranslateModule.instance();
                module.setEnabled(!module.enabled());
                return true;
            }
            if (isInside(event.x(), event.y(), contentRight() - 174, langY, 174, 18)) {
                ChatTranslateModule module = ChatTranslateModule.instance();
                module.setOutgoingTargetLanguage(
                        TranslationLanguage.nextOutgoingTarget(module.outgoingTargetLanguage()));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void saveAndClose() {
        if (timeoutBox != null && apiKeyBox != null) {
            try {
                int timeout = Integer.parseInt(timeoutBox.getValue().trim());
                if (!ChatTranslateModule.instance().saveConfiguration(
                        apiKeyBox.getValue(), ChatTranslateModule.instance().outgoingTargetLanguage(), timeout)) {
                    setErrorMessage("无法保存翻译配置，旧文件保持不变");
                    return;
                }
            } catch (NumberFormatException exception) {
                setErrorMessage("超时必须是数字，范围为 5000–60000 ms");
                return;
            }
        }
        super.saveAndClose();
    }
}
