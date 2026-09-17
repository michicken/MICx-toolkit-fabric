package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatMessageStylesTest {
    @Test
    void generatedFeedbackHasMicxPrefixAndSuccessColor() {
        var component = ChatMessageStyles.feedback("ready");
        assertEquals("[MICx] ready", component.getString());
        assertEquals(ChatMessageStyles.SUCCESS, component.getStyle().getColor().getValue());
    }

    @Test
    void fullPowerupNamesUseCanonicalKinds() {
        // 归一化实现已挪到 PowerUpHudRules（HUD 与计时表共用），这里只保留回归点
        assertEquals("insta", PowerUpHudRules.canonicalKind("Insta Kill"));
        assertEquals("shopping", PowerUpHudRules.canonicalKind("Shopping Spree"));
        assertEquals("dg", PowerUpHudRules.canonicalKind("Double Gold"));
        assertTrue(ChatMessageStyles.feedback("x").getStyle().getColor() != null);
    }
}
