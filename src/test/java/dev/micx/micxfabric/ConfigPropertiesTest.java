package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigPropertiesTest {
    @TempDir
    Path temp;

    @Test
    void loadsForgeLegacyCategoriesAndTypedValues() throws Exception {
        Path legacy = temp.resolve("MICxToolkit_Zombies.cfg");
        Files.writeString(legacy, "zombies {\n"
                + "    B:overlayEnabled=false\n"
                + "    I:topHudY=42\n"
                + "}\n");

        Properties properties = ConfigProperties.load(temp.resolve("missing.properties"), legacy);

        assertFalse(ConfigProperties.bool(properties, "overlayEnabled", true));
        assertEquals(42, ConfigProperties.integer(properties, "topHudY", 2, 0, 100));
    }

    @Test
    void loadsChamsLegacyRangeAndAppliesConfiguredBounds() throws Exception {
        Path legacy = temp.resolve("MICxToolkit_ModelChams.cfg");
        Files.writeString(legacy, "chams {\n"
                + "    I:range=999\n"
                + "}\n");

        Properties properties = ConfigProperties.load(temp.resolve("missing.properties"), legacy);

        assertEquals(128, ConfigProperties.integer(properties, "range", 48, 8, 128));
    }

    @Test
    void currentPropertiesTakePrecedenceAndInvalidValuesUseFallback() throws Exception {
        Path current = temp.resolve("zombies.properties");
        Path legacy = temp.resolve("legacy.cfg");
        Files.writeString(current, "overlayEnabled=true\ntopHudY=not-a-number\n");
        Files.writeString(legacy, "zombies { B:overlayEnabled=false I:topHudY=99 }\n");

        Properties properties = ConfigProperties.load(current, legacy);

        assertTrue(ConfigProperties.bool(properties, "overlayEnabled", false));
        assertEquals(7, ConfigProperties.integer(properties, "topHudY", 7, 0, 100));
        assertFalse(ConfigProperties.bool(properties, "broken", false));
    }
}
