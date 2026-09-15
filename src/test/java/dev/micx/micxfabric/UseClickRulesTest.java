package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 左右键互换修复的离线回归：注入目标、改键识别、面板文案。 */
class UseClickRulesTest {

    @Test
    void injectsOnlyWhenTheUseKeyIsBound() {
        assertTrue(UseClickRules.shouldInject(false));
        assertFalse(UseClickRules.shouldInject(true));
    }

    @Test
    void detectsSwappedOrChangedBindings() {
        // 左右键互换：使用键默认右键(1) → 现在绑在左键(0)
        assertTrue(UseClickRules.rebound(false, 1, 0));
        // 没改过：右键 → 右键
        assertFalse(UseClickRules.rebound(false, 1, 1));
        // 改到键盘键（值不同）同样算改键
        assertTrue(UseClickRules.rebound(false, 1, 82));
        // 未绑定不算改键，面板走「未绑定」分支
        assertFalse(UseClickRules.rebound(true, 1, -1));
    }

    @Test
    void mouseButtonNames() {
        assertEquals("鼠标左键", UseClickRules.mouseName(0));
        assertEquals("鼠标右键", UseClickRules.mouseName(1));
        assertEquals("鼠标中键", UseClickRules.mouseName(2));
        assertEquals("鼠标 4 键", UseClickRules.mouseName(3));
        assertEquals("鼠标键 9", UseClickRules.mouseName(9));
    }

    @Test
    void statusLineTellsWhichKeyIsClicked() {
        assertEquals("鼠标右键", UseClickRules.status(false, "鼠标右键", false));
        assertEquals("鼠标左键 · 已跟随改键", UseClickRules.status(false, "鼠标左键", true));
        assertEquals("使用键未绑定 · 不注入", UseClickRules.status(true, "未绑定", false));
    }
}
