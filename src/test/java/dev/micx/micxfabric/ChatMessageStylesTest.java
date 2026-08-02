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
        assertEquals("insta", ZombiesAssistModule.canonicalPowerUpKind("Insta Kill"));
        assertEquals("shopping", ZombiesAssistModule.canonicalPowerUpKind("Shopping Spree"));
        assertEquals("dg", ZombiesAssistModule.canonicalPowerUpKind("Double Gold"));
        assertTrue(ChatMessageStyles.feedback("x").getStyle().getColor() != null);
    }
}
