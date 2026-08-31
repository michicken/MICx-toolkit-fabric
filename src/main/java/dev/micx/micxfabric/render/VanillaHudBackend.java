package dev.micx.micxfabric.render;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class VanillaHudBackend implements HudBackend {

    private static final Set<Identifier> LOGGED = ConcurrentHashMap.newKeySet();

    /** 性能剖析：每抽屉帧耗时累计，[micx-hudprof] 每 3s 汇总 top8（定位掉帧抽屉用，确证后可移除）。 */
    private static final java.util.concurrent.ConcurrentHashMap<Identifier, java.util.concurrent.atomic.AtomicLong> DRAW_NANOS =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile long drawProfFlushAt;

    @Override
    public void register(Identifier id, HudDrawer drawer) {
        // 单抽屉异常隔离：一个模块 drawHud 抛错不能拖垮整帧其他 HUD；每 id 只记一次日志
        HudElementRegistry.addLast(id, (graphics, deltaTracker) -> {
            long t0 = System.nanoTime();
            try {
                drawer.draw(graphics);
            } catch (RuntimeException ex) {
                if (LOGGED.add(id)) {
                    dev.micx.micxfabric.MicxFabric.LOGGER.error("[MICx] HUD drawer {} threw; suppressing further reports", id, ex);
                }
            } finally {
                long dt = System.nanoTime() - t0;
                DRAW_NANOS.computeIfAbsent(id, k -> new java.util.concurrent.atomic.AtomicLong()).addAndGet(dt);
                long now = System.currentTimeMillis();
                if (now >= drawProfFlushAt) {
                    drawProfFlushAt = now + 3000L;
                    flushDrawProfile();
                }
            }
        });
    }

    /** 3s 窗口汇总：总耗时 + top8 抽屉（按累计 ms 排序）。 */
    private static void flushDrawProfile() {
        StringBuilder sb = new StringBuilder("[micx-hudprof]");
        double windowTotalMs = 0;
        for (java.util.concurrent.atomic.AtomicLong v : DRAW_NANOS.values()) windowTotalMs += v.get() / 1_000_000.0;
        sb.append(" windowTotalMs=").append(String.format("%.1f", windowTotalMs));
        DRAW_NANOS.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                .limit(8)
                .forEach(e -> sb.append(' ').append(e.getKey().getPath()).append('=')
                        .append(String.format("%.2f", e.getValue().get() / 1_000_000.0)).append("ms"));
        dev.micx.micxfabric.MicxFabric.LOGGER.info("{}", sb);
        DRAW_NANOS.clear();
    }
}
