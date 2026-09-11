package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** § legacy 文本处理：解析（of）与剥色（stripFormatting，ChatCopy 用）。 */
class LegacyTextTest {

    private static final String S = "\u00a7";

    @Test
    void stripsStandardColorAndFormatCodes() {
        assertEquals("Hello", LegacyText.stripFormatting(S + "aHello"));
        assertEquals("Hello world", LegacyText.stripFormatting(S + "cHello " + S + "7world"));
        // 格式码（粗体/斜体/重置等）同样是 § + 一位
        assertEquals("MICx x", LegacyText.stripFormatting(S + "a" + S + "lMICx" + S + "r x"));
        assertEquals("Round 5", LegacyText.stripFormatting(S + "6Round " + S + "15"));
    }

    @Test
    void stripsHexColorCodesWithoutLeavingDigitsBehind() {
        // 26.2 hex 颜色：§#RRGGBB 连后面 6 位一起移除（既有 replaceAll("§.") 会残留 FF0000）
        assertEquals("RED", LegacyText.stripFormatting(S + "#FF0000RED"));
        assertEquals("a]b", LegacyText.stripFormatting("a" + S + "#00FF00]b"));
        // 不是合法 6 位 hex → 至少吃掉 §#，保留其余文本
        assertEquals("GGGGGG", LegacyText.stripFormatting(S + "#GGGGGG"));
    }

    @Test
    void handlesTrailingLoneSectionSign() {
        assertEquals("abc", LegacyText.stripFormatting("abc" + S));
        assertEquals("abc", LegacyText.stripFormatting("abc" + S + "a"));
    }

    @Test
    void plainTextAndEmptyInputStayIntact() {
        assertEquals("sword & shield", LegacyText.stripFormatting("sword & shield"));
        assertEquals("", LegacyText.stripFormatting(""));
        assertEquals("", LegacyText.stripFormatting(null));
    }
}
