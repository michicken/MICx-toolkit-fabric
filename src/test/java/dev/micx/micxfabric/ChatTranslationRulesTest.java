package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 聊天翻译规则：[T] 入站判定、去重后缀剥离、出站安全清洗与语言代码。 */
class ChatTranslationRulesTest {

    @Test
    void displayedMessageAllowsChineseEnglishMixedButNotCommands() {
        assertTrue(ChatTranslationRules.shouldTranslateDisplayedMessage("rush left now"));
        assertTrue(ChatTranslationRules.shouldTranslateDisplayedMessage("先开门再来 AA"));
        assertTrue(ChatTranslationRules.shouldTranslateDisplayedMessage("  mixed 中英 text "));
        assertFalse(ChatTranslationRules.shouldTranslateDisplayedMessage("/p invite abc"));
        assertFalse(ChatTranslationRules.shouldTranslateDisplayedMessage("   /w xyz"));
        assertFalse(ChatTranslationRules.shouldTranslateDisplayedMessage("   "));
        assertFalse(ChatTranslationRules.shouldTranslateDisplayedMessage(null));
    }

    @Test
    void stripsCleanerCountSuffix() {
        assertEquals("hello", ChatTranslationRules.stripCleanerCountSuffix("hello  (x2)"));
        assertEquals("hello", ChatTranslationRules.stripCleanerCountSuffix("hello (x12)"));
        assertEquals("hello (x2", ChatTranslationRules.stripCleanerCountSuffix("hello (x2"));
        assertEquals("hello (a)", ChatTranslationRules.stripCleanerCountSuffix("hello (a)"));
        assertEquals("plain", ChatTranslationRules.stripCleanerCountSuffix("plain"));
        assertNull(ChatTranslationRules.stripCleanerCountSuffix(null));
    }

    @Test
    void safeOutboundDropsControlCharsAndCommands() {
        assertEquals("a b", ChatTranslationRules.safeOutbound("a \n\r\t b"));
        assertEquals("a b", ChatTranslationRules.safeOutbound("a   b"));
        assertNull(ChatTranslationRules.safeOutbound("/kill"));
        assertNull(ChatTranslationRules.safeOutbound("   "));
        assertNull(ChatTranslationRules.safeOutbound("x".repeat(300)));
    }

    @Test
    void languageCodesRoundTripAndOutgoingTargets() {
        assertEquals(TranslationLanguage.JA, TranslationLanguage.fromCode("ja"));
        assertEquals(TranslationLanguage.ZH, TranslationLanguage.fromCode("中文"));
        assertEquals(TranslationLanguage.AUTO, TranslationLanguage.fromCode("??"));
        for (TranslationLanguage target : TranslationLanguage.outgoingTargets()) {
            assertTrue(target != TranslationLanguage.AUTO && target != TranslationLanguage.ZH);
        }
        assertEquals(TranslationLanguage.DE, TranslationLanguage.nextOutgoingTarget(TranslationLanguage.EN));
        assertEquals(TranslationLanguage.EN, TranslationLanguage.nextOutgoingTarget(TranslationLanguage.TR));
    }
}
