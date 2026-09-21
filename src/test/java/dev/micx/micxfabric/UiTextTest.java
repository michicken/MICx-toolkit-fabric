package dev.micx.micxfabric;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 面板文案的双语义开关：默认新版，切到旧版后每段文案都要回到改写前的原文。 */
class UiTextTest {

    @AfterEach
    void restoreCurrentMode() {
        UiText.setLegacy(false);
    }

    @Test
    void rewrittenTextShowsTheNewWordingByDefault() {
        UiText.setLegacy(false);
        assertFalse(UiText.legacy());
        assertEquals("剩余怪连线", UiText.of(UiText.revised("剩余怪连线", "残怪连线")));
        assertEquals("剩余怪连线", UiText.shown("剩余怪连线", "残怪连线"));
    }

    @Test
    void legacyModeShowsTheOriginalWording() {
        UiText.setLegacy(true);
        assertTrue(UiText.legacy());
        assertEquals("残怪连线", UiText.of(UiText.revised("剩余怪连线", "残怪连线")));
        assertEquals("残怪连线", UiText.shown("剩余怪连线", "残怪连线"));
    }

    @Test
    void toggleFlipsBothWays() {
        UiText.setLegacy(false);
        UiText.toggle();
        assertTrue(UiText.legacy());
        UiText.toggle();
        assertFalse(UiText.legacy());
    }

    @Test
    void unchangedTextReadsTheSameInBothModes() {
        UiText.Txt text = UiText.unchanged("回合计时");
        UiText.setLegacy(false);
        assertEquals("回合计时", UiText.of(text));
        UiText.setLegacy(true);
        assertEquals("回合计时", UiText.of(text));
    }

    @Test
    void modeLabelsFollowTheMode() {
        UiText.setLegacy(false);
        assertEquals("新版", UiText.modeLabel());
        assertEquals("旧版", UiText.nextModeLabel());
        UiText.setLegacy(true);
        assertEquals("旧版", UiText.modeLabel());
        assertEquals("新版", UiText.nextModeLabel());
    }

    @Test
    void missingTextResolvesToEmptyInsteadOfThrowing() {
        assertEquals("", UiText.of((UiText.Txt) null));
    }

    @Test
    void nullSidesAreRejectedAtConstruction() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> UiText.revised(null, "旧"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> UiText.revised("新", null));
    }
}
