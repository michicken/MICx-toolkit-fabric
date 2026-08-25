package dev.micx.micxfabric;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 把 1.8.9 风格的 §legacy 字符串解析成带样式的 Component。
 * 26.2 的 Component.literal 不解析 § 码，直接画会丢失全部颜色——
 * 这是移植后主 HUD 颜色与 Forge 不一致的根因。
 *
 * <p>26.2 的 ChatFormatting 没有颜色/修饰判定方法，这里用码表判断。</p>
 */
public final class LegacyText {

    private static final String COLOR_CODES = "0123456789abcdef";

    private LegacyText() {
    }

    /** 无 § 码时返回原样 literal（渲染层的默认色仍生效）。 */
    public static Component of(String legacy) {
        if (legacy == null || legacy.isEmpty()) return Component.empty();
        if (legacy.indexOf('\u00a7') < 0) return Component.literal(legacy);
        MutableComponent root = Component.empty();
        ChatFormatting color = null;
        boolean bold = false;
        boolean italic = false;
        int i = 0;
        StringBuilder plain = new StringBuilder();
        while (i < legacy.length()) {
            char ch = legacy.charAt(i);
            if (ch == '\u00a7' && i + 1 < legacy.length()) {
                char code = Character.toLowerCase(legacy.charAt(i + 1));
                // 1.8.9 语义：码只影响其后的文本，先按旧样式刷出已累积段
                ChatFormatting prevColor = color;
                boolean prevBold = bold;
                boolean prevItalic = italic;
                ChatFormatting byCode = ChatFormatting.getByCode(code);
                if (byCode == ChatFormatting.RESET) {
                    color = null;
                    bold = false;
                    italic = false;
                } else if (byCode != null && COLOR_CODES.indexOf(code) >= 0) {
                    color = byCode;
                    bold = false;
                    italic = false;
                } else {
                    if (code == 'l' || code == 'L') bold = true;
                    if (code == 'o' || code == 'O') italic = true;
                }
                if (plain.length() > 0) {
                    root.append(styled(plain.toString(), prevColor, prevBold, prevItalic));
                    plain.setLength(0);
                }
                i += 2;
                continue;
            }
            plain.append(ch);
            i++;
        }
        if (plain.length() > 0) root.append(styled(plain.toString(), color, bold, italic));
        return root;
    }

    private static MutableComponent styled(String text, ChatFormatting color, boolean bold, boolean italic) {
        MutableComponent part = Component.literal(text);
        if (color != null) part = part.withStyle(color);
        if (bold) part = part.withStyle(ChatFormatting.BOLD);
        if (italic) part = part.withStyle(ChatFormatting.ITALIC);
        return part;
    }
}
