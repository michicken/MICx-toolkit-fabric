package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 更新规则的纯函数：版本比较、该不该装、文件名归属、sha256 形状。 */
class UpdateRulesTest {

    @Test
    void versionSegmentsCompareAsNumbersNotText() {
        assertTrue(UpdateRules.compare("0.2.123", "0.2.9") > 0, "123 必须大于 9，不能退化成字典序");
        assertTrue(UpdateRules.compare("0.2.9", "0.2.123") < 0);
        assertTrue(UpdateRules.compare("0.3.0", "0.2.99") > 0);
        assertEquals(0, UpdateRules.compare("0.2.123", "0.2.123"));
    }

    @Test
    void missingSegmentsCountAsZero() {
        assertEquals(0, UpdateRules.compare("1.2", "1.2.0"));
        assertTrue(UpdateRules.compare("1.2.1", "1.2") > 0);
    }

    @Test
    void nonNumericSegmentsFallBackToTextSoNothingThrows() {
        assertTrue(UpdateRules.compare("0.2.123-rc1", "0.2.123") > 0);
        assertEquals(0, UpdateRules.compare("", ""));
        assertEquals(0, UpdateRules.compare(null, "0.2.1"));
    }

    @Test
    void installWaitsOutThePublishDelay() {
        long now = 1_700_000_000_000L;
        long delay = 30L * 60_000L;
        assertTrue(UpdateRules.installable("0.2.123", "0.2.124", now - 31L * 60_000L, now, delay),
                "过了发布时间 + 延迟就该装");
        assertFalse(UpdateRules.installable("0.2.123", "0.2.124", now - 29L * 60_000L, now, delay),
                "撤回窗口内不能装");
    }

    @Test
    void installOnlyEverMovesForward() {
        long now = 1_700_000_000_000L;
        assertFalse(UpdateRules.installable("0.2.123", "0.2.123", 0L, now, 0L), "同版本不用装");
        assertFalse(UpdateRules.installable("0.2.124", "0.2.123", 0L, now, 0L), "本地更新（开发版）不降级");
    }

    @Test
    void missingPublishTimeMeansImmediate() {
        long now = 1_700_000_000_000L;
        assertTrue(UpdateRules.installable("0.2.123", "0.2.124", 0L, now, 30L * 60_000L));
    }

    @Test
    void onlyOurOwnJarNamesQualify() {
        assertTrue(UpdateRules.isOwnJar("micx-fabric-0.2.124.jar"));
        assertFalse(UpdateRules.isOwnJar("micx-fabric-0.2.124.jar.bak"), "备份不算自己的 jar");
        assertFalse(UpdateRules.isOwnJar("SkillShare-MICx-1.3.0.jar"));
        assertFalse(UpdateRules.isOwnJar("MICx-ZBHelpStart.jar"));
        assertFalse(UpdateRules.isOwnJar("micx-fabric-0.2.124.zip"));
        assertFalse(UpdateRules.isOwnJar("micx-fabric-.jar"));
        assertFalse(UpdateRules.isOwnJar(null));
    }

    @Test
    void backupNameKeepsTheOriginalAround() {
        assertEquals("micx-fabric-0.2.123.jar.bak", UpdateRules.backupName("micx-fabric-0.2.123.jar"));
    }

    @Test
    void sha256ShapeIsChecked() {
        assertTrue(UpdateRules.isSha256("a".repeat(64)));
        assertTrue(UpdateRules.isSha256("0123456789abcdef".repeat(4)));
        assertFalse(UpdateRules.isSha256("a".repeat(63)));
        assertFalse(UpdateRules.isSha256("z".repeat(64)));
        assertFalse(UpdateRules.isSha256(null));
    }
}
