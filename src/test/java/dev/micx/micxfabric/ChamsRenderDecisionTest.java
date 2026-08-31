package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChamsRenderDecisionTest {
    @Test
    void appliesToAllTargetsInsideInclusiveRangeRegardlessOfOcclusion() {
        // v4：遮挡状态不参与决策，范围内目标一律应用（合成对可见实体无感）
        assertEquals(ChamsRenderDecision.Result.APPLY,
                decide(48 * 48, 48, true));
        assertEquals(ChamsRenderDecision.Result.APPLY,
                decide(10, 48, false));
        assertEquals(ChamsRenderDecision.Result.VANILLA,
                decide(48 * 48 + 0.01, 48, true));
    }

    @Test
    void rejectsMissingContextAndInvalidDistances() {
        assertEquals(ChamsRenderDecision.Result.VANILLA,
                ChamsRenderDecision.decide(false, true, true, true, 1, 48, true, false));
        assertEquals(ChamsRenderDecision.Result.VANILLA,
                ChamsRenderDecision.decide(true, false, true, true, 1, 48, true, false));
        assertEquals(ChamsRenderDecision.Result.VANILLA,
                ChamsRenderDecision.decide(true, true, true, true, Double.NaN, 48, true, false));
        assertEquals(ChamsRenderDecision.Result.VANILLA,
                ChamsRenderDecision.decide(true, true, true, true, -1, 48, true, false));
        assertEquals(ChamsRenderDecision.Result.VANILLA,
                ChamsRenderDecision.decide(true, true, true, true, Double.POSITIVE_INFINITY, 48, true, false));
    }

    @Test
    void clampsRangeBeforeApplying() {
        assertEquals(ChamsRenderDecision.Result.APPLY,
                decide(8 * 8 - 0.01, 1, true));
        assertEquals(ChamsRenderDecision.Result.APPLY,
                decide(8 * 8, 1, true));
        assertEquals(ChamsRenderDecision.Result.APPLY,
                decide(128 * 128, 1000, true));
    }

    @Test
    void doesNotReplaceAnAlreadyActiveRender() {
        assertEquals(ChamsRenderDecision.Result.VANILLA,
                ChamsRenderDecision.decide(true, true, true, true, 1, 48, true, true));
    }

    private static ChamsRenderDecision.Result decide(double distanceSq, int range, boolean blocked) {
        return ChamsRenderDecision.decide(true, true, true, true, distanceSq, range, blocked, false);
    }
}
