package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** KeyChord 组合键纯逻辑：归一化/解析/序列化/配置迁移。 */
class KeyChordTest {

    @Test
    void normalizeRemovesZeroAndDuplicates() {
        assertArrayEquals(new int[]{42, 0, 0}, KeyChord.normalize(new int[]{0, 42, 42}));
        assertArrayEquals(KeyChord.EMPTY, KeyChord.normalize(null));
        // 超过 3 键只保留前 3 个有效键
        assertArrayEquals(new int[]{1, 2, 3}, KeyChord.normalize(new int[]{1, 1, 2, 0, 3, 4, 5}));
    }

    @Test
    void parseAcceptsCommaAndLegacySingleInt() {
        assertArrayEquals(new int[]{-99, 340, 0}, KeyChord.parse("-99, 340"));
        assertArrayEquals(new int[]{42, 0, 0}, KeyChord.parse("42"));
        assertArrayEquals(KeyChord.EMPTY, KeyChord.parse("garbage"));
        assertArrayEquals(KeyChord.EMPTY, KeyChord.parse(null));
        assertArrayEquals(KeyChord.EMPTY, KeyChord.parse(""));
    }

    @Test
    void formatRoundTripsWithParse() {
        int[] chord = new int[]{341, -101};
        assertEquals("341,-101", KeyChord.format(chord));
        assertArrayEquals(KeyChord.normalize(chord), KeyChord.parse(KeyChord.format(chord)));
        assertEquals("", KeyChord.format(KeyChord.EMPTY));
    }

    @Test
    void isEmptyAndPrimaryBehave() {
        assertTrue(KeyChord.isEmpty(KeyChord.EMPTY));
        assertTrue(KeyChord.isEmpty(null));
        assertFalse(KeyChord.isEmpty(KeyChord.single(-100)));
        assertEquals(341, KeyChord.primary(new int[]{341, 344}));
        assertEquals(0, KeyChord.primary(KeyChord.EMPTY));
    }

    @Test
    void readConfigPrefersChordFieldAndMigratesLegacyInt() {
        Properties both = new Properties();
        both.setProperty("toggleKeys", "340,42");
        both.setProperty("toggleKey", "77");
        assertArrayEquals(new int[]{340, 42, 0}, KeyChord.readConfig(both, "toggleKeys", "toggleKey", 0));

        Properties legacyOnly = new Properties();
        legacyOnly.setProperty("toggleKey", "88");
        assertArrayEquals(new int[]{88, 0, 0}, KeyChord.readConfig(legacyOnly, "toggleKeys", "toggleKey", 0));

        Properties none = new Properties();
        assertArrayEquals(KeyChord.EMPTY, KeyChord.readConfig(none, "toggleKeys", "toggleKey", 0));
    }

    @Test
    void writeConfigKeepsLegacyFieldInSync() {
        Properties p = new Properties();
        KeyChord.writeConfig(p, "toggleKeys", "toggleKey", new int[]{340, 341});
        assertEquals("340,341", p.getProperty("toggleKeys"));
        assertEquals("340", p.getProperty("toggleKey"));

        Properties cleared = new Properties();
        cleared.setProperty("toggleKeys", "340");
        KeyChord.writeConfig(cleared, "toggleKeys", "toggleKey", KeyChord.EMPTY);
        assertEquals("", cleared.getProperty("toggleKeys"));
        assertEquals("0", cleared.getProperty("toggleKey"));
    }

    @Test
    void displayFormatsHumanReadableLabel() {
        assertEquals("未绑定", KeyChord.display(KeyChord.EMPTY));
        assertEquals("LMB + Shift", KeyChord.display(new int[]{-100, 340, 0}));
        // GLFW: 341=LControl, 342=LAlt, 343=LSuper(Cmd)
        assertEquals("Ctrl + Alt + J", KeyChord.display(new int[]{341, 342, 74}));
        assertEquals("Cmd + Shift + LMB", KeyChord.display(new int[]{343, 340, -100}));
    }
}
