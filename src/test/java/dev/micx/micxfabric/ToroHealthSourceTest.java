package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T4 防回归（tasks.md T4-TR1）：ToroHealth 必须使用无限准心射线，
 * 禁止回落到原版 crosshairPickEntity（有限距离 + 被墙挡）。
 */
class ToroHealthSourceTest {

    @Test
    void tickUsesInfiniteRaycastInsteadOfVanillaCrosshairPick() throws Exception {
        Path source = Path.of("src/main/java/dev/micx/micxfabric/ToroHealthModule.java");
        assertTrue(Files.exists(source), "source missing");
        String content = Files.readString(source);
        assertFalse(content.contains("crosshairPickEntity"),
                "ToroHealthModule must not read client.crosshairPickEntity");
        assertTrue(content.contains("entitiesForRendering"),
                "raycast must scan all loaded entities");
        assertTrue(content.contains(".clip(eye, end)"),
                "raycast must clip candidate AABBs against the infinite ray");
    }
}
