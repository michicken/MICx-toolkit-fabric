package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * PacketLog 面板：开关、标记、复制路径、最近事件。
 *
 * <p>面板只做"看一眼最近发生了什么"，完整日志在文件里。
 */
public final class PacketLogConfigScreen extends ModuleConfigScreen {
    private static final int MAX_SHOWN = 14;
    private final List<Hit> hits = new ArrayList<>();

    private record Hit(int x, int y, int w, int h, Runnable action) {
    }

    public PacketLogConfigScreen(Screen parent) {
        super(parent, "PacketLog", "封包日志 · 抓买弹现场");
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        hits.clear();
        PacketLogModule module = PacketLogModule.instance();
        Minecraft client = Minecraft.getInstance();
        int pointerY = mouseY + scrollOffset();

        section(graphics, "STATUS / 状态", y);
        y += 20;
        graphics.text(font, "启用 / Enable", contentLeft(), y + 4, TEXT);
        boolean toggleHovered = isInside(mouseX, pointerY, contentRight() - 44, y + 1, 44, 16);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.enabled(), toggleHovered);
        hits.add(new Hit(contentRight() - 44, y + 1, 44, 16, () -> module.setEnabled(!module.enabled())));
        y += 24;
        graphics.text(font, module.enabled()
                        ? "记录中 · 已写 " + module.writtenLines() + " 行"
                        : "已关闭（默认关，纯旁观不改包）",
                contentLeft(), y, module.enabled() ? ON : TEXT_DIM);
        y += 14;
        y = wrapped(graphics, "日志：" + module.logFilePath(), contentLeft(), y, TEXT_FAINT, contentWidth()) + 8;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, "OPTIONS / 选项", y);
        y += 20;
        graphics.text(font, "记录全部（含移动包）", contentLeft(), y + 4, TEXT);
        boolean allHovered = isInside(mouseX, pointerY, contentRight() - 44, y + 1, 44, 16);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.logAll(), allHovered);
        hits.add(new Hit(contentRight() - 44, y + 1, 44, 16, () -> module.setLogAll(!module.logAll())));
        y += 24;
        y = wrapped(graphics, "关着时只记：交互（右键方块/实体/空气）、点界面格子、聊天命令、自定义通道；"
                + "入站只记开界面、界面内容/被关、聊天、标题/动作栏、自定义通道、服务端拉回、音效、计分。"
                + "快捷键 " + new InputBinding(module.bindingCode()).label() + " = 插标记线。",
                contentLeft(), y, TEXT_DIM, contentWidth()) + 8;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, "ACTIONS / 操作", y);
        y += 20;
        int buttonW = 96;
        boolean markHovered = isInside(mouseX, pointerY, contentLeft(), y, buttonW, 18);
        drawButton(graphics, "打一条标记", contentLeft(), y, buttonW, 18, markHovered);
        hits.add(new Hit(contentLeft(), y, buttonW, 18, () -> module.mark("面板标记")));
        int copyX = contentLeft() + buttonW + 6;
        boolean copyHovered = isInside(mouseX, pointerY, copyX, y, buttonW, 18);
        drawButton(graphics, "复制日志路径", copyX, y, buttonW, 18, copyHovered);
        hits.add(new Hit(copyX, y, buttonW, 18, () -> {
            if (client != null && client.keyboardHandler != null) {
                client.keyboardHandler.setClipboard(module.logFilePath());
            }
        }));
        int clearX = copyX + buttonW + 6;
        boolean clearHovered = isInside(mouseX, pointerY, clearX, y, buttonW, 18);
        drawButton(graphics, "清空面板", clearX, y, buttonW, 18, clearHovered);
        hits.add(new Hit(clearX, y, buttonW, 18, module::clearRecent));
        y += 26;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, "RECENT / 最近事件", y);
        y += 18;
        List<String> recent = module.recent();
        if (recent.isEmpty()) {
            graphics.text(font, module.enabled() ? "暂无事件——去游戏里做点事（比如买一发子弹）" : "开开关再看",
                    contentLeft(), y, TEXT_FAINT);
            y += 16;
        } else {
            int from = Math.max(0, recent.size() - MAX_SHOWN);
            for (int i = from; i < recent.size(); i++) {
                boolean marker = recent.get(i).startsWith("----");
                graphics.text(font, trim(recent.get(i), contentWidth()),
                        contentLeft(), y, marker ? AMBER : TEXT_DIM);
                y += 11;
            }
            graphics.text(font, "共 " + recent.size() + " 行（面板只显示最近 " + MAX_SHOWN + " 行，完整看日志文件）",
                    contentLeft(), y, TEXT_FAINT);
            y += 16;
        }
        setContentHeight(y - contentTop() + scrollOffset());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double pointerY = event.y() + scrollOffset();
            for (Hit hit : hits) {
                if (isInside(event.x(), pointerY, hit.x(), hit.y(), hit.w(), hit.h())) {
                    hit.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
