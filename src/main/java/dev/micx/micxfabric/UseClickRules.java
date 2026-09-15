package dev.micx.micxfabric;

/**
 * 「连点」注入目标该点哪个键的纯逻辑，以及面板状态文案。
 *
 * <p>历史 bug：注入时写死 {@code KeyMapping.getDefaultKey()}（默认 = 鼠标右键）。玩家把左右键
 * 互换（左键→使用、右键→攻击）之后，默认右键在 {@code KeyMapping.MAP} 里已经属于攻击键，
 * {@code KeyMapping.click(默认右键)} 加的是 keyAttack 的点击计数 —— 「右键连点」于是变成
 * 20 CPS 平 A（2026-09-15 用户报的 bug）。
 *
 * <p>正确做法是点「当前绑定」那个键：MAP 用当前绑定做索引，click 只会加在占用该键的动作上，
 * 等价于玩家亲手按下去 —— 绑鼠标、绑键盘都成立，所以这里只按绑定本身判断，不看键类型。
 */
public final class UseClickRules {
    private UseClickRules() {
    }

    /** 未绑定时不能注入：没有动作占用该键，click 找不到目标。 */
    public static boolean shouldInject(boolean unbound) {
        return !unbound;
    }

    /** 当前绑定已经不是默认键 —— 玩家改过键，左右键互换就是这种。 */
    public static boolean rebound(boolean unbound, int defaultValue, int currentValue) {
        return !unbound && defaultValue != currentValue;
    }

    /** 鼠标键编号 → 中文名（GLFW 编号：0 左键 / 1 右键 / 2 中键）。 */
    public static String mouseName(int button) {
        return switch (button) {
            case 0 -> "鼠标左键";
            case 1 -> "鼠标右键";
            case 2 -> "鼠标中键";
            case 3 -> "鼠标 4 键";
            case 4 -> "鼠标 5 键";
            default -> "鼠标键 " + button;
        };
    }

    /** 面板状态行：连点现在实际点在哪个键上。 */
    public static String status(boolean unbound, String currentLabel, boolean rebound) {
        if (unbound) return "使用键未绑定 · 不注入";
        return rebound ? currentLabel + " · 已跟随改键" : currentLabel;
    }
}
