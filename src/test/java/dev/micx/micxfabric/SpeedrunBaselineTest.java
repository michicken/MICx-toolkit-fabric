package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeedrunBaselineTest {
    @Test
    void baselineCandidatesCoverTheGameConfigRoot() {
        // 0.2.86 的 10832（1:08:45 个人记录）放在游戏 config 根：config/baseline.json。
        // 早期实现只找 config/MICxToolkit/ 与 Forge 风格旧名，落空后静默回退打包的 11015，
        // 表现就是「1:08 的记录一直没显示」——这个顺序必须钉住。
        assertEquals(List.of("MICxToolkit/baseline.json", "baseline.json",
                        "MICxToolkit_baseline.json"),
                SpeedrunBaseline.candidates("baseline.json", "MICxToolkit_baseline.json"));
    }

    @Test
    void slotBLooksInTheSameThreePlaces() {
        List<String> b = SpeedrunBaseline.candidates("baseline2.json", "MICxToolkit_baseline_2.json");
        assertTrue(b.contains("MICxToolkit/baseline2.json"), "模块目录优先");
        assertTrue(b.contains("baseline2.json"), "B 槽位同样要认 config 根下的文件");
        assertTrue(b.contains("MICxToolkit_baseline_2.json"), "Forge 风格旧名要保留");
    }
}
