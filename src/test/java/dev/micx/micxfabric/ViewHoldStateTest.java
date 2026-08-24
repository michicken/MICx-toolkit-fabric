package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewHoldStateTest {
    @Test
    void pressEngagesTargetViewAndReleaseRestoresFirstPerson() {
        ViewHoldState state = new ViewHoldState(ViewHoldState.VIEW_FRONT);
        assertEquals(ViewHoldState.NO_ACTION, state.observe(false, false));
        assertEquals(ViewHoldState.VIEW_FRONT, state.observe(true, false));
        assertTrue(state.isManaging());
        assertEquals(0, state.observe(false, false));
        assertFalse(state.isManaging());
    }

    @Test
    void blockedWhileDownKeepsManaging() {
        ViewHoldState state = new ViewHoldState(ViewHoldState.VIEW_BEHIND);
        assertEquals(ViewHoldState.VIEW_BEHIND, state.observe(true, false));
        // GUI 打开时按键不算按下，但本模块按住过 → 负责恢复第一人称
        assertEquals(0, state.observe(false, true));
    }

    @Test
    void playerOwnF5IsNeverTouched() {
        ViewHoldState state = new ViewHoldState(ViewHoldState.VIEW_BEHIND);
        // 从未按过绑定键：任何状态都不干预
        assertEquals(ViewHoldState.NO_ACTION, state.observe(false, true));
        assertEquals(ViewHoldState.NO_ACTION, state.observe(false, false));
    }

    @Test
    void targetChangeAppliesNextTick() {
        ViewHoldState state = new ViewHoldState(ViewHoldState.VIEW_BEHIND);
        state.setTarget(ViewHoldState.VIEW_FRONT);
        assertEquals(ViewHoldState.VIEW_FRONT, state.observe(true, false));
    }
}
