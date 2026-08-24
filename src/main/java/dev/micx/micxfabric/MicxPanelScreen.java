package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * MICx 的 Fabric 26.2 主控制台。
 *
 * <p>这里刻意保留 1.8.9 面板的固定双栏结构；模块状态直接从运行时读取，
 * 不在 Screen 内复制一份开关状态。</p>
 */
public final class MicxPanelScreen extends Screen {
    private static final int BG_SCRIM = 0xE60C0E12;
    private static final int BG_PANEL = 0xFF14181E;
    private static final int BG_RAISED = 0xFF1B2028;
    private static final int BG_HOVER = 0xFF232A34;
    private static final int LINE = 0xFF2A313B;
    private static final int LINE_HI = 0xFF39424F;
    private static final int TEXT = 0xFFE7EAEE;
    private static final int TEXT_DIM = 0xFF98A0AB;
    private static final int TEXT_FAINT = 0xFF5C6572;
    private static final int AMBER = 0xFFE8A73E;
    private static final int AMBER_DIM = 0xFF8A6A2A;
    private static final int ON = 0xFF57B98C;
    private static final int OFF = 0xFF525A64;

    private static final int MARGIN_Y = 22;
    private static final int PAD = 14;
    private static final int HEADER_H = 46;
    private static final int FOOTER_H = 20;
    private static final int RAIL_W = 172;
    private static final int GROUP_H = 20;
    private static final int ROW_H = 22;

    private final Screen parent;
    private final List<RailEntry> entries = new ArrayList<>();
    private String selectedId;
    private String selectedSubId;
    private String listeningKeybindId;
    private int railScroll;
    private int railContentHeight;
    private String errorMessage;

    private int cardX;
    private int cardY;
    private int cardW;
    private int cardH;
    private int headerBottom;
    private int footerTop;
    private int railX;
    private int railW;
    private int paneX;
    private int paneW;
    private int hudLayoutX;
    private int hudLayoutY;
    private int hudLayoutW;

    public MicxPanelScreen(Screen parent) {
        super(Component.literal("MICx Toolkit"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        cardW = Math.min(486, Math.max(0, width - 20));
        cardX = (width - cardW) / 2;
        cardY = Math.min(MARGIN_Y, Math.max(0, height / 2));
        cardH = Math.max(0, height - cardY * 2);
        headerBottom = Math.min(cardY + HEADER_H, cardY + cardH);
        footerTop = Math.max(headerBottom, cardY + cardH - FOOTER_H);
        railX = cardX;
        railW = Math.min(RAIL_W, cardW / 2);
        paneX = cardX + railW + 1;
        paneW = Math.max(0, cardW - railW - 1);

        buildEntries();
        if (selectedId == null || ModulePanelRegistry.get(selectedId) == null) {
            selectedId = entries.stream()
                    .filter(entry -> !entry.header)
                    .map(entry -> entry.id)
                    .findFirst()
                    .orElse(null);
            selectedSubId = null;
        }
        clampRailScroll();
    }

    private void buildEntries() {
        entries.clear();
        int contentHeight = 6;
        for (String group : ModulePanelRegistry.GROUP_ORDER) {
            List<ModulePanelDescriptor> modules = ModulePanelRegistry.inGroup(group);
            if (modules.isEmpty()) continue;
            entries.add(RailEntry.header(group, contentHeight));
            contentHeight += GROUP_H;
            for (ModulePanelDescriptor descriptor : modules) {
                entries.add(RailEntry.module(descriptor.id(), contentHeight));
                contentHeight += ROW_H;
                for (ModulePanelRegistry.SubmoduleDescriptor submodule : ModulePanelRegistry.submodules(descriptor.id())) {
                    entries.add(RailEntry.submodule(descriptor.id(), submodule.id(), contentHeight));
                    contentHeight += ROW_H;
                }
            }
        }
        railContentHeight = contentHeight + 6;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BG_SCRIM);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        drawPanel(graphics, mouseX, mouseY);
    }

    private void drawPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, BG_PANEL);
        graphics.fill(cardX, cardY, cardX + cardW, cardY + 1, LINE_HI);
        outline(graphics, cardX, cardY, cardW, cardH, LINE);

        drawHeader(graphics);
        graphics.fill(cardX, headerBottom, cardX + cardW, headerBottom + 1, LINE);
        graphics.fill(paneX - 1, headerBottom, paneX, footerTop, LINE);
        graphics.fill(cardX, footerTop, cardX + cardW, footerTop + 1, LINE);

        drawRail(graphics, mouseX, mouseY);
        drawDetails(graphics, mouseX, mouseY);
        drawFooter(graphics);
    }

    private void drawHeader(GuiGraphicsExtractor graphics) {
        int x = cardX + PAD;
        graphics.text(font, Component.literal("MICx Toolkit"), x, cardY + 8, TEXT, true);
        graphics.text(font, Component.literal("模块控制台 · Fabric 26.2"), x, cardY + 29, TEXT_DIM);

        int total = 0;
        int enabled = 0;
        for (ModulePanelDescriptor descriptor : ModulePanelRegistry.all()) {
            total++;
            if (descriptor.isMigrated() && descriptor.module().enabled()) enabled++;
        }
        String stats = total + " modules · " + enabled + " on";
        graphics.text(font, stats, cardX + cardW - PAD - font.width(stats), cardY + 29, AMBER);

        int tutorialX = cardX + cardW - PAD - 58;
        hudLayoutW = 78;
        hudLayoutX = tutorialX - 6 - hudLayoutW;
        hudLayoutY = cardY + 7;
        drawDisabledEntry(graphics, "Tutorial", tutorialX, hudLayoutY, 58);
        boolean hovered = hudLayoutX <= lastMouseX && lastMouseX < hudLayoutX + hudLayoutW
                && hudLayoutY <= lastMouseY && lastMouseY < hudLayoutY + 16;
        drawHeaderEntry(graphics, "HUD Layout", hudLayoutX, hudLayoutY, hudLayoutW, hovered);
    }

    private int lastMouseX;
    private int lastMouseY;

    private void drawDisabledEntry(GuiGraphicsExtractor graphics, String label, int x, int y, int w) {
        graphics.fill(x, y, x + w, y + 16, BG_RAISED);
        outline(graphics, x, y, w, 16, LINE);
        int textWidth = font.width(label);
        graphics.text(font, label, x + Math.max(2, (w - textWidth) / 2), y + 4, TEXT_FAINT);
    }

    private void drawHeaderEntry(GuiGraphicsExtractor graphics, String label, int x, int y, int w, boolean hovered) {
        graphics.fill(x, y, x + w, y + 16, hovered ? BG_HOVER : BG_RAISED);
        outline(graphics, x, y, w, 16, hovered ? LINE_HI : LINE);
        int textWidth = font.width(label);
        graphics.text(font, label, x + Math.max(2, (w - textWidth) / 2), y + 4, hovered ? TEXT : TEXT_DIM);
    }

    private void drawRail(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int clipRight = Math.max(cardX, paneX - 2);
        int clipBottom = Math.max(headerBottom, footerTop - 1);
        graphics.enableScissor(railX + 1, headerBottom + 1, clipRight, clipBottom);
        for (RailEntry entry : entries) {
            int y = entry.contentY - railScroll + headerBottom;
            entry.screenY = y;
            if (y + entry.height < headerBottom || y > footerTop) continue;
            if (entry.header) {
                ModulePanelRegistry.GroupMetadata group = ModulePanelRegistry.group(entry.group);
                graphics.text(font, group.displayName(), railX + PAD, y + 6, AMBER_DIM);
                int groupX = railX + PAD + font.width(group.displayName()) + 6;
                graphics.text(font, group.chineseName(), groupX, y + 6, TEXT_FAINT);
                continue;
            }

            boolean selected = entry.id.equals(selectedId) && java.util.Objects.equals(entry.subId, selectedSubId);
            boolean hovered = mouseX >= railX && mouseX < paneX - 1
                    && mouseY >= y && mouseY < y + entry.height
                    && mouseY >= headerBottom && mouseY < footerTop;
            if (selected) {
                graphics.fill(railX + 1, y, paneX - 1, y + entry.height, BG_RAISED);
                graphics.fill(railX + 1, y, railX + 3, y + entry.height, AMBER);
            } else if (hovered) {
                graphics.fill(railX + 1, y, paneX - 1, y + entry.height, BG_HOVER);
            }

            ModulePanelDescriptor descriptor = ModulePanelRegistry.get(entry.id);
            if (descriptor == null) continue;
            boolean on = descriptor.isMigrated() && descriptor.module().enabled();
            if (entry.subId != null) {
                ModulePanelRegistry.SubmoduleDescriptor submodule = ModulePanelRegistry.submodule(entry.id, entry.subId);
                int textX = railX + PAD + 20;
                String label = "· " + (submodule == null ? entry.subId : submodule.displayName());
                graphics.text(font, trim(label, Math.max(0, paneX - 6 - textX)), textX,
                        y + 7, selected ? TEXT : TEXT_DIM);
                if (submodule != null) {
                    int chineseX = textX + font.width(label) + 6;
                    if (chineseX + font.width(submodule.chineseName()) < paneX - 5) {
                        graphics.text(font, submodule.chineseName(), chineseX, y + 7, TEXT_FAINT);
                    }
                }
                continue;
            }
            drawStateDot(graphics, railX + PAD - 2, y + entry.height / 2, on);
            int textX = railX + PAD + 10;
            graphics.text(font, trim(descriptor.displayName(), Math.max(0, paneX - 6 - textX)), textX,
                    y + 7, selected ? TEXT : TEXT_DIM);
            int chineseX = textX + font.width(descriptor.displayName()) + 6;
            if (chineseX + font.width(descriptor.chineseName()) < paneX - 5) {
                graphics.text(font, descriptor.chineseName(), chineseX, y + 7, TEXT_FAINT);
            }
            if (!descriptor.isMigrated()) {
                graphics.text(font, "·", paneX - 15, y + 7, TEXT_FAINT);
            }
        }
        graphics.disableScissor();
        drawScrollbar(graphics);
    }

    private void drawDetails(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (selectedId == null) return;
        ModulePanelDescriptor descriptor = ModulePanelRegistry.get(selectedId);
        if (descriptor == null) return;

        int x = paneX + PAD;
        int right = paneX + paneW - PAD;
        int y = headerBottom + 12;

        if (selectedSubId != null) {
            ModulePanelRegistry.SubmoduleDescriptor submodule = ModulePanelRegistry.submodule(selectedId, selectedSubId);
            if (submodule != null) {
                graphics.text(font, submodule.displayName(), x, y, TEXT, true);
                int chineseX = x + font.width(submodule.displayName()) + 7;
                if (chineseX < right - 50) graphics.text(font, submodule.chineseName(), chineseX, y + 2, TEXT_DIM);
                y += 14;
                graphics.text(font, selectedId + " / " + selectedSubId, x, y, TEXT_FAINT);
                y += 18;
                for (net.minecraft.util.FormattedCharSequence line : font.split(Component.literal(submodule.description()), Math.max(20, right - x))) {
                    graphics.text(font, line, x, y, TEXT_DIM);
                    y += font.lineHeight;
                }
                y += 5;
                graphics.fill(x, y, right, y + 1, LINE);
                y += 12;
                if (!descriptor.isMigrated()) {
                    graphics.text(font, "NOT MIGRATED / 未迁移", x, y + 3, AMBER_DIM);
                    graphics.text(font, "此子分区仅保留原 1.8.9 入口，Fabric 运行逻辑尚未迁移。", x, y + 15, TEXT_FAINT);
                } else {
                    boolean parentOn = descriptor.module().enabled();
                    graphics.text(font, "Enable", x, y + 3, TEXT);
                    graphics.text(font, "切换 ZombiesAssist 父模块；Display / Alerts / Auto 不是独立模块。",
                            x, y + 15, TEXT_FAINT);
                    int toggleX = right - 44;
                    drawToggle(graphics, toggleX, y + 2, 44, 16, parentOn,
                            mouseX >= toggleX && mouseX < toggleX + 44
                                    && mouseY >= y + 2 && mouseY < y + 18);
                    y += 34;
                    if (submodule.hasConfigScreen()) {
                        drawAction(graphics, x, y, right - x, 18, "Configure  ›",
                                mouseX >= x && mouseX < right && mouseY >= y && mouseY < y + 18);
                    }
                }
                return;
            }
        }
        String title = descriptor.displayName();
        graphics.text(font, title, x, y, TEXT, true);
        int chineseX = x + font.width(title) + 7;
        if (chineseX < right - 50) graphics.text(font, descriptor.chineseName(), chineseX, y + 2, TEXT_DIM);
        y += 14;
        graphics.text(font, descriptor.id(), x, y, TEXT_FAINT);

        boolean on = descriptor.isMigrated() && descriptor.module().enabled();
        drawStatusBadge(graphics, right - (descriptor.isMigrated() ? 48 : 96),
                headerBottom + 12, descriptor);

        y += 18;
        int descriptionWidth = Math.max(20, right - x);
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(descriptor.description()), descriptionWidth);
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            graphics.text(font, line, x, y, TEXT_DIM);
            y += font.lineHeight;
        }
        y += 5;
        graphics.fill(x, y, right, y + 1, LINE);
        y += 12;

        if (!descriptor.isMigrated()) {
            graphics.text(font, Component.literal("NOT MIGRATED / 未迁移"), x, y + 3, AMBER_DIM);
            graphics.text(font, Component.literal("Fabric 运行逻辑尚未迁移；此处不会伪造开关。"), x, y + 15, TEXT_FAINT);
            return;
        }

        graphics.text(font, Component.literal("Enable"), x, y + 3, TEXT);
        graphics.text(font, Component.literal("打开或关闭该模块"), x, y + 15, TEXT_FAINT);
        int toggleX = right - 44;
        int toggleY = y + 2;
        drawToggle(graphics, toggleX, toggleY, 44, 16, on,
                mouseX >= toggleX && mouseX < toggleX + 44 && mouseY >= toggleY && mouseY < toggleY + 16);
        y += 34;

        if (descriptor.hasKeybind()) {
            int keybindY = y;
            drawKeybindControl(graphics, descriptor, x, right, keybindY, mouseX, mouseY);
            y += 34;
        }

        if (descriptor.hasConfigScreen()) {
            drawAction(graphics, x, y, right - x, 18, "Configure  ›",
                    mouseX >= x && mouseX < right && mouseY >= y && mouseY < y + 18);
        } else {
            graphics.text(font, Component.literal("No extra settings"), x, y + 3, TEXT_DIM);
            graphics.text(font, Component.literal("此模块当前没有额外可调项"), x, y + 15, TEXT_FAINT);
        }

        if (errorMessage != null && !errorMessage.isBlank()) {
            int errorY = Math.min(footerTop - 12, y + 34);
            graphics.text(font, trim(errorMessage, Math.max(20, right - x)), x, errorY, 0xFFD2685C);
        }
    }

    private void drawFooter(GuiGraphicsExtractor graphics) {
        String footer = "ESC 关闭 · 左键选择频道 · 左栏滚轮滚动";
        graphics.text(font, footer, railX + PAD, footerTop + 6, TEXT_FAINT);
        String unavailable = "Tutorial 未迁移 · HUD Layout 可编辑";
        graphics.text(font, unavailable, cardX + cardW - PAD - font.width(unavailable), footerTop + 6, TEXT_FAINT);
    }

    private void drawStatusBadge(GuiGraphicsExtractor graphics, int x, int y, ModulePanelDescriptor descriptor) {
        String label = descriptor.statusLabel();
        int width = descriptor.isMigrated() ? 48 : Math.max(88, font.width(label) + 18);
        boolean enabled = descriptor.isMigrated() && descriptor.module().enabled();
        int border = descriptor.isBlocked() ? AMBER_DIM : (enabled ? ON : LINE);
        int textColor = descriptor.isBlocked() ? AMBER_DIM : (enabled ? ON : TEXT_DIM);
        graphics.fill(x, y, x + width, y + 14, BG_RAISED);
        outline(graphics, x, y, width, 14, border);
        if (descriptor.isMigrated()) drawStateDot(graphics, x + 8, y + 7, enabled);
        graphics.text(font, label, x + (descriptor.isMigrated() ? 14 : 7), y + 3, textColor);
    }

    private void drawAction(GuiGraphicsExtractor graphics, int x, int y, int w, int h, String label, boolean hovered) {
        graphics.fill(x, y, x + w, y + h, hovered ? BG_HOVER : BG_RAISED);
        outline(graphics, x, y, w, h, hovered ? LINE_HI : LINE);
        graphics.text(font, label, x + 7, y + 5, hovered ? TEXT : TEXT_DIM);
    }

    private void drawToggle(GuiGraphicsExtractor graphics, int x, int y, int w, int h, boolean enabled, boolean hovered) {
        int track = enabled ? 0xFF1E3A31 : BG_RAISED;
        graphics.fill(x, y, x + w, y + h, track);
        outline(graphics, x, y, w, h, hovered ? LINE_HI : LINE);
        int knobWidth = w / 2;
        int knobX = enabled ? x + w - knobWidth : x;
        graphics.fill(knobX, y, knobX + knobWidth, y + h, enabled ? ON : OFF);
        String label = enabled ? "开" : "关";
        graphics.text(font, label, knobX + (knobWidth - font.width(label)) / 2, y + 4, 0xFF0E1013);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics) {
        int viewHeight = Math.max(0, footerTop - headerBottom);
        if (railContentHeight <= viewHeight || viewHeight <= 0) return;
        int trackX = paneX - 4;
        graphics.fill(trackX, headerBottom, trackX + 3, footerTop, 0x33000000);
        int thumbHeight = Math.max(18, Math.round(viewHeight * (viewHeight / (float) railContentHeight)));
        int maxScroll = Math.max(1, railContentHeight - viewHeight);
        int thumbY = headerBottom + Math.round((viewHeight - thumbHeight) * (railScroll / (float) maxScroll));
        graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, AMBER_DIM);
        graphics.fill(trackX, thumbY, trackX + 3, thumbY + Math.min(6, thumbHeight), AMBER);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= railX && mouseX < paneX && mouseY >= headerBottom && mouseY < footerTop) {
            int delta = scrollY > 0 ? -ROW_H : ROW_H;
            railScroll += delta;
            clampRailScroll();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (listeningKeybindId != null) {
            ModulePanelDescriptor listeningDescriptor = ModulePanelRegistry.get(listeningKeybindId);
            if (listeningDescriptor != null && listeningDescriptor.hasKeybind()) {
                listeningDescriptor.keybind().setKeyCode(-100 + event.button());
                listeningKeybindId = null;
                errorMessage = null;
                return true;
            }
            listeningKeybindId = null;
        }
        if (event.button() != 0) return false;
        double mouseX = event.x();
        double mouseY = event.y();
        int tutorialX = cardX + cardW - PAD - 58;
        int layoutW = 78;
        int layoutX = tutorialX - 6 - layoutW;
        int layoutY = cardY + 7;
        if (mouseX >= layoutX && mouseX < layoutX + layoutW
                && mouseY >= layoutY && mouseY < layoutY + 16) {
            minecraft.setScreenAndShow(new HudLayoutEditorScreen(this));
            return true;
        }

        if (mouseX >= railX && mouseX < paneX - 1 && mouseY >= headerBottom && mouseY < footerTop) {
            for (RailEntry entry : entries) {
                if (entry.header) continue;
                if (mouseY >= entry.screenY && mouseY < entry.screenY + entry.height
                        && entry.screenY + entry.height >= headerBottom
                        && entry.screenY <= footerTop) {
                    selectedId = entry.id;
                    selectedSubId = entry.subId;
                    errorMessage = null;
                    return true;
                }
            }
            return true;
        }

        ModulePanelDescriptor descriptor = selectedId == null ? null : ModulePanelRegistry.get(selectedId);
        if (descriptor == null || !descriptor.isControllable()) return false;
        int x = paneX + PAD;
        int right = paneX + paneW - PAD;

        if (selectedSubId != null) {
            ModulePanelRegistry.SubmoduleDescriptor submodule =
                    ModulePanelRegistry.submodule(selectedId, selectedSubId);
            if (submodule != null) {
                int submoduleToggleY = findSubmoduleToggleY(submodule);
                if (mouseX >= right - 44 && mouseX < right
                        && mouseY >= submoduleToggleY && mouseY < submoduleToggleY + 16) {
                    try {
                        ModuleRuntime.setEnabled(descriptor.id(), !descriptor.module().enabled());
                        errorMessage = null;
                    } catch (RuntimeException exception) {
                        errorMessage = "无法切换 ZombiesAssist：" + exception.getClass().getSimpleName();
                    }
                    return true;
                }
                int submoduleConfigY = findSubmoduleConfigY(submodule);
                if (submodule.hasConfigScreen()
                        && mouseX >= x && mouseX < right
                        && mouseY >= submoduleConfigY && mouseY < submoduleConfigY + 18) {
                    try {
                        Screen config = submodule.createConfigScreen(this);
                        if (config != null) minecraft.setScreenAndShow(config);
                        else errorMessage = "此子模块的配置页面尚未迁移";
                    } catch (RuntimeException exception) {
                        errorMessage = "无法打开子模块配置页面：" + exception.getClass().getSimpleName();
                    }
                    return true;
                }
            }
            return false;
        }
        int toggleY = findToggleY(descriptor);
        if (mouseX >= right - 44 && mouseX < right && mouseY >= toggleY && mouseY < toggleY + 16) {
            try {
                ModuleRuntime.setEnabled(descriptor.id(), !descriptor.module().enabled());
                errorMessage = null;
            } catch (RuntimeException exception) {
                errorMessage = "无法切换模块：" + exception.getClass().getSimpleName();
            }
            return true;
        }

        if (descriptor.hasKeybind()) {
            int keybindY = findKeybindY(descriptor);
            int clearW = 42;
            int bindW = 58;
            int clearX = right - clearW;
            int bindX = clearX - 4 - bindW;
            if (mouseX >= bindX && mouseX < bindX + bindW
                    && mouseY >= keybindY + 1 && mouseY < keybindY + 19) {
                listeningKeybindId = descriptor.id();
                errorMessage = null;
                return true;
            }
            if (mouseX >= clearX && mouseX < right
                    && mouseY >= keybindY + 1 && mouseY < keybindY + 19) {
                descriptor.keybind().clear();
                listeningKeybindId = null;
                errorMessage = null;
                return true;
            }
        }

        if (descriptor.hasConfigScreen()) {
            int configY = findConfigY(descriptor);
            if (mouseX >= x && mouseX < right && mouseY >= configY && mouseY < configY + 18) {
                try {
                    Screen config = descriptor.createConfigScreen(this);
                    if (config == null) {
                        errorMessage = "此模块的配置页面尚未迁移";
                    } else {
                        minecraft.setScreenAndShow(config);
                    }
                } catch (RuntimeException exception) {
                    errorMessage = "无法打开配置页面：" + exception.getClass().getSimpleName();
                }
                return true;
            }
        }
        return false;
    }

    private int findSubmoduleConfigY(ModulePanelRegistry.SubmoduleDescriptor submodule) {
        int y = headerBottom + 12 + 14 + 18;
        int right = paneX + paneW - PAD;
        int descriptionWidth = Math.max(20, right - (paneX + PAD));
        int lineCount = Math.max(1, font.split(Component.literal(submodule.description()), descriptionWidth).size());
        y += lineCount * font.lineHeight + 5 + 1 + 12;
        return y + 34;
    }

    private int findSubmoduleToggleY(ModulePanelRegistry.SubmoduleDescriptor submodule) {
        return findSubmoduleConfigY(submodule) - 32;
    }

    private int findToggleY(ModulePanelDescriptor descriptor) {
        int y = headerBottom + 12 + 14 + 18;
        int right = paneX + paneW - PAD;
        int descriptionWidth = Math.max(20, right - (paneX + PAD));
        int lineCount = Math.max(1, font.split(Component.literal(descriptor.description()), descriptionWidth).size());
        y += Math.max(1, lineCount) * font.lineHeight + 5 + 1 + 12;
        return y + 2;
    }

    private void drawKeybindControl(GuiGraphicsExtractor graphics,
                                    ModulePanelDescriptor descriptor,
                                    int x, int right, int y,
                                    int mouseX, int mouseY) {
        ModuleKeybind keybind = descriptor.keybind();
        boolean listening = descriptor.id().equals(listeningKeybindId);
        String value = listening ? "按键或鼠标键 · ESC 取消" : keybind.keyLabel();
        graphics.text(font, "Bind", x, y + 3, TEXT);
        graphics.text(font, keybind.description(), x, y + 15, TEXT_FAINT);
        int clearW = 42;
        int bindW = 58;
        int clearX = right - clearW;
        int bindX = clearX - 4 - bindW;
        drawAction(graphics, bindX, y + 1, bindW, 18, listening ? "监听中" : "Bind",
                mouseX >= bindX && mouseX < bindX + bindW && mouseY >= y + 1 && mouseY < y + 19);
        drawAction(graphics, clearX, y + 1, clearW, 18, "Clear",
                mouseX >= clearX && mouseX < right && mouseY >= y + 1 && mouseY < y + 19);
        graphics.text(font, trim(value, Math.max(20, bindX - x - 8)), x, y + 27,
                listening ? AMBER : TEXT_DIM);
    }

    private int findKeybindY(ModulePanelDescriptor descriptor) {
        return findToggleY(descriptor) + 32;
    }

    private int findConfigY(ModulePanelDescriptor descriptor) {
        int y = findToggleY(descriptor) + 32;
        if (descriptor.hasKeybind()) y += 34;
        return y;
    }

    private void clampRailScroll() {
        int viewHeight = Math.max(0, footerTop - headerBottom);
        int max = Math.max(0, railContentHeight - viewHeight);
        railScroll = Math.max(0, Math.min(railScroll, max));
    }

    private static void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static void drawStateDot(GuiGraphicsExtractor graphics, int centerX, int centerY, boolean enabled) {
        int color = enabled ? ON : OFF;
        if (enabled) {
            graphics.fill(centerX - 1, centerY - 3, centerX + 2, centerY + 3, color);
            graphics.fill(centerX - 3, centerY - 1, centerX + 3, centerY + 2, color);
        } else {
            graphics.fill(centerX - 1, centerY - 1, centerX + 1, centerY + 1, color);
        }
    }

    private String trim(String value, int maxWidth) {
        if (value == null || maxWidth <= 0) return "";
        if (font.width(value) <= maxWidth) return value;
        return font.plainSubstrByWidth(value, maxWidth, true);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listeningKeybindId != null) {
            if (event.isEscape()) {
                listeningKeybindId = null;
                errorMessage = null;
                return true;
            }
            ModulePanelDescriptor descriptor = ModulePanelRegistry.get(listeningKeybindId);
            if (descriptor != null && descriptor.hasKeybind()) {
                descriptor.keybind().setKeyCode(event.key());
                listeningKeybindId = null;
                errorMessage = null;
                return true;
            }
            listeningKeybindId = null;
        }
        if (event.isEscape()) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }


    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }

    private static final class RailEntry {
        private final boolean header;
        private final String group;
        private final String id;
        private final String subId;
        private final int contentY;
        private final int height;
        private int screenY;

        private RailEntry(boolean header, String group, String id, String subId, int contentY, int height) {
            this.header = header;
            this.group = group;
            this.id = id;
            this.subId = subId;
            this.contentY = contentY;
            this.height = height;
        }

        private static RailEntry header(String group, int y) {
            return new RailEntry(true, group, null, null, y, GROUP_H);
        }

        private static RailEntry module(String id, int y) {
            return new RailEntry(false, null, id, null, y, ROW_H);
        }

        private static RailEntry submodule(String id, String subId, int y) {
            return new RailEntry(false, null, id, subId, y, ROW_H);
        }
    }
}
