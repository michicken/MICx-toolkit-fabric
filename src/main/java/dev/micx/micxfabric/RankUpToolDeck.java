package dev.micx.micxfabric;

import java.util.Random;

/**
 * 变形句洗牌袋：一轮内每条模板只用一次，跨轮不让同一句连着出现两次。
 *
 * <p>为什么不用「每次随机挑一条」：随机抽样短窗口内会重复（20 句里抽 3 次撞同一句的概率不低），
 * 而重复正是聊天里最扎眼的部分。洗牌袋保证一轮 {@code size} 次内绝对不重复，
 * 只有跨轮才可能出现同句，再用首元素交换把「跨轮立即重复」也压掉。
 *
 * <p>纯逻辑 + 注入 {@link Random}，可离线回归（见 RankUpToolMessagesTest）。
 */
public final class RankUpToolDeck {
    private final int size;
    private final int[] order;
    private final Random random;
    private int position;
    private boolean filled;
    private int lastIndex = -1;

    public RankUpToolDeck(int size, Random random) {
        this.size = Math.max(0, size);
        this.order = new int[this.size];
        this.random = random == null ? new Random() : random;
    }

    public int size() {
        return size;
    }

    /** 本轮还剩几句没发。 */
    public int remaining() {
        if (size <= 0) return 0;
        return filled ? size - position : size;
    }

    /** 下一句开始重新洗一轮（面板「重洗句库」用）。 */
    public void reshuffle() {
        filled = false;
        position = 0;
    }

    /** 取下一句的模板下标；句库为空返回 -1（调用方回落固定文本）。 */
    public int next() {
        if (size <= 0) return -1;
        if (!filled || position >= size) {
            refill();
        }
        int index = order[position++];
        lastIndex = index;
        return index;
    }

    private void refill() {
        for (int i = 0; i < size; i++) {
            order[i] = i;
        }
        for (int i = size - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int swap = order[i];
            order[i] = order[j];
            order[j] = swap;
        }
        // 跨轮不重复：新一轮的第一句如果正好是上一轮的收尾，就和第二句换位。
        if (size > 1 && order[0] == lastIndex) {
            int swap = order[0];
            order[0] = order[1];
            order[1] = swap;
        }
        position = 0;
        filled = true;
    }
}
