package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FullbrightRuntimeTest {
    @Test
    void fullbrightFactorIsTheMaximumSupportedValue() {
        assertEquals(1.0f, FullbrightRuntime.FULLBRIGHT_FACTOR);
    }

    @Test
    void fullbrightFactorIsFiniteAndWithinLightmapRange() {
        float factor = FullbrightRuntime.FULLBRIGHT_FACTOR;
        assertEquals(true, Float.isFinite(factor));
        assertEquals(true, factor >= 0.0f && factor <= 1.0f);
    }
}
