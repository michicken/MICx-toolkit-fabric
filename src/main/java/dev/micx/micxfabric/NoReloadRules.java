package dev.micx.micxfabric;

import java.util.ArrayList;
import java.util.List;

/**
 * NoReload 纯规则（不依赖 Minecraft 运行环境，可单测）。Forge 同名规则原样移植。
 *
 * <p>移植自 BridgerCat 1.4.9 NoReload，去双预设 / QuickSwitch 依赖，单组参数。
 * 覆盖决策：有效槽构建、槽有效性、暂停判定、轮换索引、发包参数、时间换算。
 */
public final class NoReloadRules {

    private NoReloadRules() { }

    /** 盔甲架暂停距离阈值（格，含）：准星指向盔甲架且距离 ≤3 格时暂停（原版）。 */
    public static final double ARMOR_STAND_PAUSE_DIST = 3.0;
    /** 快捷栏 → 背包索引偏移：背包索引 = 36 + hotbarSlot（ContainerPlayer 快捷栏 36-44）。 */
    public static final int INVENTORY_OFFSET = 36;
    /** 放回槽：把手上物品放回主背包 41 号槽（原版照抄，服务端语义实战有效）。 */
    public static final int STASH_SLOT = 41;
    /** 发包节流（ns）：12ms——点击包频率上限（原版 12000000ns）。 */
    public static final long SWITCH_THROTTLE_NS = 12_000_000L;
    /** Delay 参数范围与默认（ms）：原版 50-80，默认 67。 */
    public static final double DELAY_MIN_MS = 50.0;
    public static final double DELAY_MAX_MS = 80.0;
    public static final double DELAY_DEFAULT_MS = 67.0;

    /**
     * 有效槽构建：slot2/3/4 开关 → 轮换序列（原版顺序 2,3,4）；全关兜底 [1]（槽 2）。
     *
     * @return 轮换序列（hotbar 索引：0 起，返回 1/2/3 = 快捷栏 2/3/4 格）
     */
    public static List<Integer> buildActiveSlots(boolean s2, boolean s3, boolean s4) {
        List<Integer> slots = new ArrayList<Integer>();
        if (s2) slots.add(1);
        if (s3) slots.add(2);
        if (s4) slots.add(3);
        if (slots.isEmpty()) slots.add(1);
        return slots;
    }

    /**
     * 槽有效性：非空且非银色染料（原版排除：Items.dye + EnumDyeColor.SILVER）。
     *
     * @param stackEmpty  槽位物品是否为空
     * @param isSilverDye 物品是否为银色染料
     */
    public static boolean isSlotValid(boolean stackEmpty, boolean isSilverDye) {
        return !stackEmpty && !isSilverDye;
    }

    /** 盔甲架暂停：距离 ≤3 格（含）→ 暂停（原版 {@code <= 3.0}）。 */
    public static boolean pauseForArmorStand(double distance) {
        return distance <= ARMOR_STAND_PAUSE_DIST;
    }

    /** 槽 1 暂停：当前快捷栏槽 == 0（槽 1）→ 暂停（主武器槽不参与轮换，原版）。 */
    public static boolean isSlot1(int currentItem) {
        return currentItem == 0;
    }

    /**
     * 轮换索引：在有效槽序列中取"下一把"。
     * currentIndex 有效时直接 +1 循环；无效时定位当前槽从它后面开始，
     * 当前槽不在序列中 → 从头开始（原版 foundCurrentSlot 分支）。
     */
    public static int nextSlotIndex(List<Integer> validSlots, int currentSlot, int currentIndex) {
        if (validSlots == null || validSlots.isEmpty()) return 0;
        int size = validSlots.size();
        if (currentIndex >= 0 && currentIndex < size) {
            return (currentIndex + 1) % size;
        }
        int found = validSlots.indexOf(Integer.valueOf(currentSlot));
        if (found >= 0) return (found + 1) % size;
        return 0;
    }

    /** 背包索引：36 + hotbarSlot（快捷栏槽在 ContainerPlayer 中的索引）。 */
    public static int inventorySlotFor(int hotbarSlot) {
        return INVENTORY_OFFSET + hotbarSlot;
    }

    /** ms → ns。 */
    public static long msToNanos(double ms) {
        return (long) (ms * 1_000_000.0);
    }
}
