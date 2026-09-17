package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JamProtectionSequenceTest {
    @Test
    void selectsClicksAndRestoresInOrder() {
        JamProtectionSequence sequence = new JamProtectionSequence(2, 0);
        assertEquals(JamProtectionSequence.Kind.SELECT, sequence.advance(0, 0L).kind);
        assertEquals(JamProtectionSequence.Kind.NONE, sequence.advance(2, 25L).kind);
        assertEquals(JamProtectionSequence.Kind.RELOAD, sequence.advance(2, 50L).kind);
        assertEquals(0, sequence.advance(2, 75L).slot);
        assertEquals(JamProtectionSequence.Kind.COMPLETE, sequence.advance(2, 100L).kind);
    }

    @Test
    void manualSlotChangeCancelsBeforeDrop() {
        JamProtectionSequence sequence = new JamProtectionSequence(2, 0);
        sequence.advance(0, 0L);
        assertEquals(JamProtectionSequence.Kind.CANCEL, sequence.advance(1, 25L).kind);
    }

    @Test
    void targetTimeoutCancels() {
        JamProtectionSequence sequence = new JamProtectionSequence(2, 0);
        sequence.advance(0, 0L);
        assertEquals(JamProtectionSequence.Kind.CANCEL, sequence.advance(0, 100L).kind);
    }
}
