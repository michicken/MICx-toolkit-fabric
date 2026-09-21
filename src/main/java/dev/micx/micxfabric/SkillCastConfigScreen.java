package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/** Functional SkillCast settings matching the original single key binding. */
public final class SkillCastConfigScreen extends ModuleConfigScreen {
    private boolean listening;

    public SkillCastConfigScreen(Screen parent) {
        super(parent, UiText.shown("技能一键释放", "SkillCast"), UiText.shown("SkillCast · 原生使用时序", "技能释放 · 原生使用时序"));
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        SkillCastModule module = SkillCastModule.instance();
        section(graphics, UiText.shown("开关与状态", "STATUS / 状态"), y);
        y += 20;
        graphics.text(font, UiText.shown("启用模块", "Enable"), contentLeft(), y + 4, TEXT);
        graphics.text(font, "单次触发：切槽 5、调用原生 useItem、释放后切回。", contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.enabled(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("快捷键", "KEYBIND / 快捷键"), y);
        y += 20;
        graphics.text(font, UiText.shown("主键", "Primary"), contentLeft(), y + 4, TEXT);
        String label = listening ? "按任意键或鼠标键 · ESC 取消" : new InputBinding(module.keyCode()).label();
        drawButton(graphics, label, contentRight() - 156, y, 156, 18,
                isInside(mouseX, mouseY, contentRight() - 156, y, 156, 18));
        y += 34;
        y = wrapped(graphics,
                "触发只使用 26.2 原生 MultiPlayerGameMode，不手工构造自定义技能包；无世界、暂停或配置页时不会发送操作。",
                contentLeft(), y, TEXT_DIM, contentWidth());
        setContentHeight(y - contentTop() + scrollOffset());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (listening && event.button() != 0) {
            SkillCastModule.instance().setKeyCode(-100 + event.button());
            listening = false;
            return true;
        }
        if (event.button() == 0) {
            int y = contentTop() + 21;
            if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
                SkillCastModule.instance().setEnabled(!SkillCastModule.instance().enabled());
                return true;
            }
            y += 38 + 14 + 20;
            if (isInside(event.x(), event.y(), contentRight() - 156, y, 156, 18)) {
                listening = true;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listening) {
            if (event.isEscape()) {
                listening = false;
            } else {
                SkillCastModule.instance().setKeyCode(event.key());
                listening = false;
            }
            return true;
        }
        return super.keyPressed(event);
    }
}
