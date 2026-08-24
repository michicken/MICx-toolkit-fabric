package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NoReloadRulesTest {

    /* ---- buildActiveSlots ---- */

    @Test
    public void buildActiveSlotsFollowsSwitchesInOrder() {
        // 23：槽 2,3（hotbar 索引 1,2）
        List<Integer> s23 = NoReloadRules.buildActiveSlots(true, true, false);
        assertEquals(2, s23.size());
        assertEquals(Integer.valueOf(1), s23.get(0));
        assertEquals(Integer.valueOf(2), s23.get(1));
        // 234
        List<Integer> s234 = NoReloadRules.buildActiveSlots(true, true, true);
        assertEquals(3, s234.size());
        assertEquals(Integer.valueOf(1), s234.get(0));
        assertEquals(Integer.valueOf(2), s234.get(1));
        assertEquals(Integer.valueOf(3), s234.get(2));
        // 24 / 34
        assertEquals(2, NoReloadRules.buildActiveSlots(true, false, true).size());
        assertEquals(2, NoReloadRules.buildActiveSlots(false, true, true).size());
        // 单槽
        assertEquals(1, NoReloadRules.buildActiveSlots(true, false, false).size());
    }

    @Test
    public void buildActiveSlotsFallsBackToSlot2WhenAllOff() {
        // 全关兜底 [1]（槽 2）——原版语义：至少一把武器轮换
        List<Integer> slots = NoReloadRules.buildActiveSlots(false, false, false);
        assertEquals(1, slots.size());
        assertEquals(Integer.valueOf(1), slots.get(0));
    }

    /* ---- isSlotValid ---- */

    @Test
    public void slotValidRejectsNullAndSilverDye() {
        assertFalse(NoReloadRules.isSlotValid(true, false));   // 空槽
        assertFalse(NoReloadRules.isSlotValid(true, true));
        assertFalse(NoReloadRules.isSlotValid(false, true));   // 银色染料
        assertTrue(NoReloadRules.isSlotValid(false, false));   // 普通武器
    }

    /* ---- pause 判定 ---- */

    @Test
    public void pauseForArmorStandInclusiveBoundary() {
        assertTrue(NoReloadRules.pauseForArmorStand(3.0));     // 含边界（原版 <= 3.0）
        assertTrue(NoReloadRules.pauseForArmorStand(2.9));
        assertTrue(NoReloadRules.pauseForArmorStand(0.0));
        assertFalse(NoReloadRules.pauseForArmorStand(3.1));
        assertFalse(NoReloadRules.pauseForArmorStand(10.0));
    }

    @Test
    public void slot1PauseOnlyForFirstHotbarSlot() {
        assertTrue(NoReloadRules.isSlot1(0));                  // 槽 1
        assertFalse(NoReloadRules.isSlot1(1));
        assertFalse(NoReloadRules.isSlot1(8));
    }

    /* ---- nextSlotIndex ---- */

    @Test
    public void nextSlotIndexCyclesForwardWhenIndexValid() {
        List<Integer> slots = NoReloadRules.buildActiveSlots(true, true, true);   // [1,2,3]
        assertEquals(1, NoReloadRules.nextSlotIndex(slots, 1, 0));   // 0 → 1
        assertEquals(2, NoReloadRules.nextSlotIndex(slots, 1, 1));   // 1 → 2
        assertEquals(0, NoReloadRules.nextSlotIndex(slots, 1, 2));   // 2 → 0（循环）
    }

    @Test
    public void nextSlotIndexLocatesCurrentSlotWhenIndexInvalid() {
        List<Integer> slots = NoReloadRules.buildActiveSlots(true, true, false);  // [1,2]
        // 当前槽 1（索引 0）→ 下一把 = 槽 2（索引 1）
        assertEquals(1, NoReloadRules.nextSlotIndex(slots, 1, -1));
        // 当前槽 2（索引 1）→ 循环回槽 2（索引 0）
        assertEquals(0, NoReloadRules.nextSlotIndex(slots, 2, -1));
        // 当前槽不在序列（如槽 5）→ 从头开始（原版 foundCurrentSlot=false → index 0）
        assertEquals(0, NoReloadRules.nextSlotIndex(slots, 5, -1));
    }

    @Test
    public void nextSlotIndexEmptySequenceReturnsZero() {
        assertEquals(0, NoReloadRules.nextSlotIndex(null, 1, -1));
        assertEquals(0, NoReloadRules.nextSlotIndex(new java.util.ArrayList<Integer>(), 1, 0));
    }

    /* ---- 发包参数与时间 ---- */

    @Test
    public void inventorySlotForMapsHotbarToContainerIndex() {
        assertEquals(36, NoReloadRules.inventorySlotFor(0));    // 槽 1 → 36
        assertEquals(37, NoReloadRules.inventorySlotFor(1));    // 槽 2 → 37
        assertEquals(39, NoReloadRules.inventorySlotFor(3));    // 槽 4 → 39
        assertEquals(44, NoReloadRules.inventorySlotFor(8));
        assertEquals(41, NoReloadRules.STASH_SLOT);             // 放回槽固定 41
    }

    @Test
    public void msToNanosConvertsCorrectly() {
        assertEquals(67_000_000L, NoReloadRules.msToNanos(67.0));
        assertEquals(50_000_000L, NoReloadRules.msToNanos(50.0));
        assertEquals(80_000_000L, NoReloadRules.msToNanos(80.0));
        assertEquals(0L, NoReloadRules.msToNanos(0.0));
    }

    @Test
    public void constantsMatchOriginal() {
        // 原版参数核对：Delay 50-80 默认 67；节流 12000000ns；盔甲架 3.0
        assertEquals(50.0, NoReloadRules.DELAY_MIN_MS);
        assertEquals(80.0, NoReloadRules.DELAY_MAX_MS);
        assertEquals(67.0, NoReloadRules.DELAY_DEFAULT_MS);
        assertEquals(12_000_000L, NoReloadRules.SWITCH_THROTTLE_NS);
        assertEquals(3.0, NoReloadRules.ARMOR_STAND_PAUSE_DIST);
    }
}
