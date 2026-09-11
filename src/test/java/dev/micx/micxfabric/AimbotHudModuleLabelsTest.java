package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AimbotHudModule HUD 标签组防回归：
 * labels 扁平项数必须与 KEY_IDS 一致，否则 drawHud 每帧 AIOOBE、HUD 整体不渲染
 * （0.2.72 移除 prioBaby 时 labels 漏改导致的事故）。
 */
class AimbotHudModuleLabelsTest {

    private static String[] keyIds() {
        try {
            Field f = AimbotHudModule.class.getDeclaredField("KEY_IDS");
            f.setAccessible(true);
            return (String[]) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> flatLabels() {
        List<String> flat = new ArrayList<>();
        for (String[] group : AimbotHudModule.hudLabels()) {
            assertTrue(group.length > 0, "HUD 标签组不允许出现空组（空组会错位分隔符）");
            for (String label : group) {
                assertTrue(label != null && !label.isBlank(), "HUD 标签不允许为空");
                flat.add(label);
            }
        }
        return flat;
    }

    @Test
    void flatLabelCountMatchesKeyIds() {
        assertEquals(keyIds().length, flatLabels().size(),
                "labels 扁平项数必须等于 KEY_IDS.length，否则 drawHud 越界");
    }

    @Test
    void labelOrderMatchesKeySemanticOrder() {
        // KEY_IDS = ignoreToo/ignoreGolem/ignoreSlime/prioClown/prioGiant/closest
        assertEquals(List.of("TOO", "GOL", "SLM", "CLO", "GIA", "CLS"), flatLabels());
    }

    @Test
    void noLegacyBabyLabelRemains() {
        assertTrue(flatLabels().stream().noneMatch("BAB"::equals),
                "prioBaby 已在 0.2.72 移除，HUD 不应再出现 BAB 标签");
    }
}
