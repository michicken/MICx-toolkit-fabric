package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 自动更新面板：开关 + 当前状态 + 两个动作。
 *
 * <p>状态是只读快照（{@link UpdateModule} 的字段），所以这一页没有可编辑的数值输入，
 * 也就不用管「离开页面时校验保存」那一套。
 */
public final class UpdateConfigScreen extends ModuleConfigScreen {
    private static final int BUTTON_H = 18;
    private final List<Hit> hits = new ArrayList<>();

    private record Hit(int x, int y, int w, int h, Runnable action) {
    }

    public UpdateConfigScreen(Screen parent) {
        super(parent, UiText.shown("自动更新", "AutoUpdate"),
                UiText.shown("AutoUpdate · 服务器直推", "AutoUpdate · 自动更新"));
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        UpdateModule module = UpdateModule.instance();
        hits.clear();

        section(graphics, UiText.shown("开关与状态", "STATUS / 状态"), y);
        y += 20;
        y = toggleRow(graphics, mouseX, mouseY, y,
                UiText.shown("启用模块", "Enable"),
                UiText.shown("后台定期查服务器有没有新版。", "启用自动更新检查。"),
                module.enabled(), () -> module.setEnabled(!module.enabled()));
        y = toggleRow(graphics, mouseX, mouseY, y,
                UiText.shown("自动安装", "Auto Install"),
                UiText.shown("查到的同时下载并换装，重启即生效；关掉只提示不下载。",
                        "查到新版后自动下载并换装。"),
                module.autoInstall(), () -> module.setAutoInstall(!module.autoInstall()));

        y += 4;
        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("当前情况", "VALUES / 数值"), y);
        y += 20;
        y = value(graphics, y, UiText.shown("当前版本", "Local"), module.localVersion());
        y = value(graphics, y, UiText.shown("服务器版本", "Remote"),
                module.remoteVersion().isBlank() ? "—" : module.remoteVersion());
        y = value(graphics, y, UiText.shown("最近检查", "Last Check"),
                module.lastCheckMs() <= 0L ? UiText.shown("还没查过", "never") : ago(module.lastCheckMs()));
        y = value(graphics, y, UiText.shown("状态", "State"), stateText(module));
        if (!module.detail().isBlank()) {
            y = wrapped(graphics, module.detail(), contentLeft(), y + 2, TEXT_FAINT, contentWidth()) + 6;
        }
        y += 6;

        int half = (contentWidth() - 8) / 2;
        boolean checkHovered = isInside(mouseX, mouseY, contentLeft(), y, half, BUTTON_H);
        drawButton(graphics, UiText.shown("立即检查更新", "Check now"), contentLeft(), y, half, BUTTON_H, checkHovered);
        hits.add(new Hit(contentLeft(), y, half, BUTTON_H, module::requestCheck));
        boolean openHovered = isInside(mouseX, mouseY, contentLeft() + half + 8, y, half, BUTTON_H);
        drawButton(graphics, UiText.shown("打开更新文件夹", "Open folder"),
                contentLeft() + half + 8, y, half, BUTTON_H, openHovered);
        hits.add(new Hit(contentLeft() + half + 8, y, half, BUTTON_H, UpdateConfigScreen::openUpdateFolder));
        y += BUTTON_H + 10;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("说明", "ABOUT / 说明"), y);
        y += 20;
        y = wrapped(graphics, UiText.shown(
                "启动后会自动查一次服务器上的版本清单，有新版就下到 config/MICxToolkit/update/ 并校验；"
                        + "校验通过才替换 mods/ 里的旧文件，旧的那份改名成 .bak 留着。换装当场生效不了，"
                        + "本次游戏仍然跑旧版，重启一次就完成。",
                "启动时检查服务器版本清单，有新版自动下载并换装。"), contentLeft(), y, TEXT_DIM, contentWidth()) + 8;
        y = wrapped(graphics, UiText.shown(
                "任何一步不成功——查不到、下载断了、校验不过、旧文件被占用——都只是这次没更新，不影响继续玩。",
                "任何一步失败都保持现状可玩。"), contentLeft(), y, TEXT_DIM, contentWidth()) + 8;
        setContentHeight(y - (contentTop() - scrollOffset()));
    }

    private int toggleRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y,
                          String label, String desc, boolean on, Runnable toggle) {
        graphics.text(font, label, contentLeft(), y + 4, TEXT);
        graphics.text(font, desc, contentLeft(), y + 17, TEXT_DIM);
        int x = contentRight() - 44;
        boolean hovered = isInside(mouseX, mouseY, x, y + 1, 44, 16);
        drawToggle(graphics, x, y + 1, 44, 16, on, hovered);
        hits.add(new Hit(x, y + 1, 44, 16, toggle));
        return y + 38;
    }

    private int value(GuiGraphicsExtractor graphics, int y, String label, String value) {
        graphics.text(font, label, contentLeft(), y + 3, TEXT_DIM);
        graphics.text(font, trim(value, Math.max(20, contentWidth() - 110)), contentLeft() + 100, y + 3, TEXT);
        return y + 18;
    }

    private String stateText(UpdateModule module) {
        return switch (module.state()) {
            case IDLE -> UiText.shown("待检查", "idle");
            case CHECKING -> UiText.shown("正在检查…", "checking");
            case UP_TO_DATE -> UiText.shown("已是最新", "up to date");
            case DOWNLOADING -> UiText.shown("正在下载…", "downloading");
            case INSTALLED -> UiText.shown("已换装 · 重启生效", "installed, restart to apply");
            case LOCKED -> UiText.shown("需手动替换一次", "needs manual swap");
            case FAILED -> UiText.shown("这次没成功", "failed");
        };
    }

    private String ago(long timestampMs) {
        long minutes = Math.max(0L, (System.currentTimeMillis() - timestampMs) / 60_000L);
        if (minutes < 1L) return UiText.shown("刚刚", "just now");
        if (minutes < 60L) return minutes + UiText.shown(" 分钟前", " min ago");
        return (minutes / 60L) + UiText.shown(" 小时前", " h ago");
    }

    private static void openUpdateFolder() {
        Path dir = UpdateModule.instance().updateDir();
        try {
            Files.createDirectories(dir);
            File target = dir.toFile();
            net.minecraft.util.Util.getPlatform().openFile(target);
        } catch (IOException | RuntimeException exception) {
            MicxFabric.LOGGER.warn("Unable to open the update folder", exception);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            for (Hit hit : hits) {
                if (isInside(event.x(), event.y(), hit.x(), hit.y(), hit.w(), hit.h())) {
                    hit.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
