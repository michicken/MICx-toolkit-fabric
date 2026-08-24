package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HudLayoutConfigTest {
    @TempDir
    Path temp;

    @Test
    void defaultsAndScaleBoundsAreStable() {
        HudLayoutConfig config = config("missing.properties");

        assertEquals(1.0f, config.scaleX("asr"), 0.001f);
        assertEquals(1.0f, config.scaleY("asr"), 0.001f);

        config.setScale("asr", 0.1f, 3.0f);
        assertEquals(0.5f, config.scaleX("asr"), 0.001f);
        assertEquals(2.0f, config.scaleY("asr"), 0.001f);
    }

    @Test
    void malformedAndNonFiniteValuesFallBackToOne() throws Exception {
        Path current = temp.resolve("hud-layout.properties");
        Files.writeString(current, "bad.scaleX=not-a-number\n"
                + "bad.scaleY=NaN\n"
                + "bounded.scaleX=0.1\n"
                + "bounded.scaleY=3.0\n");

        HudLayoutConfig config = new HudLayoutConfig(current, temp.resolve("missing.cfg"));

        assertEquals(1.0f, config.scaleX("bad"), 0.001f);
        assertEquals(1.0f, config.scaleY("bad"), 0.001f);
        assertEquals(0.5f, config.scaleX("bounded"), 0.001f);
        assertEquals(2.0f, config.scaleY("bounded"), 0.001f);
    }

    @Test
    void saveReloadAndResetPreserveIndependentAxes() throws Exception {
        Path current = temp.resolve("hud-layout.properties");
        HudLayoutConfig config = new HudLayoutConfig(current, temp.resolve("missing.cfg"));
        config.setScale("teammate_hp", 1.25f, 0.8f);
        config.save();

        HudLayoutConfig reloaded = new HudLayoutConfig(current, temp.resolve("missing.cfg"));
        assertEquals(1.25f, reloaded.scaleX("teammate_hp"), 0.001f);
        assertEquals(0.8f, reloaded.scaleY("teammate_hp"), 0.001f);

        reloaded.reset("teammate_hp");
        assertEquals(1.0f, reloaded.scaleX("teammate_hp"), 0.001f);
        assertEquals(1.0f, reloaded.scaleY("teammate_hp"), 0.001f);
        reloaded.setScale("asr", 1.4f, 1.1f);
        reloaded.resetAll();
        assertEquals(1.0f, reloaded.scaleX("asr"), 0.001f);
        assertEquals(1.0f, reloaded.scaleY("asr"), 0.001f);
    }

    private HudLayoutConfig config(String fileName) {
        return new HudLayoutConfig(temp.resolve(fileName), temp.resolve("missing.cfg"));
    }
}
