package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 折叠 key 的剥离规则。回归 0.2.109：翻译开启时行尾多一个 [T]，
 * (xN) 被挤到中间，旧实现剥不掉导致折叠对不上 key、旧消息不删。
 */
class ChatCleanerRulesTest {
    @Test
    void stripsCountSuffixAtEnd() {
        assertEquals("Hey", ChatCleanerRules.canonicalKey("Hey  (x3)"));
        assertEquals("Hey", ChatCleanerRules.canonicalKey("Hey  (X2)"));
    }

    @Test
    void translateTagAfterCountStillCollapses() {
        // 装饰顺序 cleaner→translate：显示文本 = "Hey  (x3)  [T]"。
        assertEquals("Hey", ChatCleanerRules.canonicalKey("Hey  (x3)  [T]"));
        assertEquals(ChatCleanerRules.canonicalKey("Hey  [T]"),
                ChatCleanerRules.canonicalKey("Hey  (x3)  [T]"));
    }

    @Test
    void translateTagBeforeCountStillCollapses() {
        // 装饰顺序 translate→cleaner：显示文本 = "Hey  [T]  (x3)"。
        assertEquals("Hey", ChatCleanerRules.canonicalKey("Hey  [T]  (x3)"));
        assertEquals("Hey", ChatCleanerRules.canonicalKey("Hey  [T]"));
    }

    @Test
    void copyTagIsStrippedAlongsideTranslateTag() {
        assertEquals("Hey", ChatCleanerRules.canonicalKey("Hey  [T]  [C]"));
    }

    @Test
    void midTextTokensAndWordAttachedTagsAreKept() {
        assertEquals("kill (x3) done", ChatCleanerRules.canonicalKey("kill (x3) done"));
        assertEquals("lobby[T]", ChatCleanerRules.canonicalKey("lobby[T]"));
    }

    @Test
    void separatorDetectionIsUnchanged() {
        assertEquals(true, ChatCleanerRules.isSeparator("-----"));
        assertEquals(false, ChatCleanerRules.isSeparator("--  [T]"));
    }
}
