package dev.micx.micxfabric;

/** Minecraft-free ordinary jam recovery sequence. RELOAD 的具体按键（左键/Q）由调用方按混合规则决定。 */
final class JamProtectionSequence {
    enum Kind { NONE, SELECT, RELOAD, RESTORE, COMPLETE, CANCEL }

    static final class Action {
        final Kind kind;
        final int slot;

        private Action(Kind kind, int slot) {
            this.kind = kind;
            this.slot = slot;
        }

        static Action none() { return new Action(Kind.NONE, -1); }
        static Action of(Kind kind, int slot) { return new Action(kind, slot); }
    }

    private static final int SWITCH = 0;
    private static final int WAIT_FOR_TARGET = 1;
    private static final int RELOAD = 2;
    private static final int RESTORE = 3;

    private final int targetSlot;
    private final int previousSlot;
    private int stage = SWITCH;
    private long waitingSince;
    private boolean finished;

    JamProtectionSequence(int targetSlot, int previousSlot) {
        this.targetSlot = targetSlot;
        this.previousSlot = previousSlot;
    }

    /** 触发保护前的原槽位（豁免进入时恢复用）。 */
    int previousSlot() {
        return previousSlot;
    }

    Action advance(int selectedSlot, long now) {
        if (finished) return Action.of(Kind.COMPLETE, -1);
        if (stage == SWITCH) {
            if (selectedSlot == targetSlot) {
                stage = RELOAD;
                return Action.none();
            }
            stage = WAIT_FOR_TARGET;
            waitingSince = now;
            return Action.of(Kind.SELECT, targetSlot);
        }
        if (stage == WAIT_FOR_TARGET) {
            if (selectedSlot == targetSlot) {
                stage = RELOAD;
                return Action.none();
            }
            if (selectedSlot != previousSlot || now - waitingSince >= 100L) {
                finished = true;
                return Action.of(Kind.CANCEL, -1);
            }
            return Action.none();
        }
        if (stage == RELOAD) {
            if (selectedSlot != targetSlot) {
                finished = true;
                return Action.of(Kind.CANCEL, -1);
            }
            stage = RESTORE;
            return Action.of(Kind.RELOAD, targetSlot);
        }
        if (selectedSlot != targetSlot) {
            finished = true;
            return Action.of(Kind.CANCEL, -1);
        }
        finished = true;
        return Action.of(Kind.RESTORE, previousSlot);
    }
}
