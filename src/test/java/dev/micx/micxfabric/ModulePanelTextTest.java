package dev.micx.micxfabric;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 面板条目与文案开关的接线：中文名/介绍跟着开关走，英文短名固定不动。
 *
 * <p>这里不在测试里枚举 {@code ModulePanelRegistry}：它的静态初始化要给每个模块取
 * 主快捷键，那些模块会去读 Fabric 配置目录，脱离游戏运行时拿不到。所以用
 * {@link UnmigratedModule} 直接构造一条描述符来验证同一段接线。
 */
class ModulePanelTextTest {

    private static ModulePanelDescriptor probe(UiText.Txt chinese, UiText.Txt description) {
        return new ModulePanelDescriptor("probe", "Probe", chinese, "core", 0, description,
                new UnmigratedModule("probe"), null, null, false, false);
    }

    @AfterEach
    void restoreCurrentMode() {
        UiText.setLegacy(false);
    }

    @Test
    void titleAndIntroductionFollowTheTextModeSwitch() {
        ModulePanelDescriptor descriptor = probe(
                UiText.revised("剩余怪连线", "残怪连线"),
                UiText.revised("回合快清完时拉线指向剩下的怪。", "回合剩余怪 ≤N 时拉黄色指示线。"));

        UiText.setLegacy(false);
        assertEquals("剩余怪连线", descriptor.chineseName());
        assertEquals("回合快清完时拉线指向剩下的怪。", descriptor.description());

        UiText.setLegacy(true);
        assertEquals("残怪连线", descriptor.chineseName());
        assertEquals("回合剩余怪 ≤N 时拉黄色指示线。", descriptor.description());
    }

    @Test
    void englishShortNameIsNotAffectedByTheSwitch() {
        ModulePanelDescriptor descriptor = probe(
                UiText.revised("经济增速", "经济速率"), UiText.revised("每 2 分钟净增长。", "每2分钟纯增长速率。"));

        UiText.setLegacy(false);
        assertEquals("Probe", descriptor.displayName());
        assertEquals("probe", descriptor.id());

        UiText.setLegacy(true);
        assertEquals("Probe", descriptor.displayName());
        assertEquals("probe", descriptor.id());
    }

    @Test
    void blankSideOfATitleIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> probe(UiText.revised(" ", "残怪连线"), UiText.unchanged("介绍")));
        assertThrows(IllegalArgumentException.class,
                () -> probe(UiText.unchanged("剩余怪连线"), UiText.revised("", "旧介绍")));
    }
}
