package dev.micx.micxfabric;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlimeEcoTrackerTest {
    @Test
    void countsOnlyGrownRemoteNaturalDeath() {
        SlimeEcoTracker tracker = new SlimeEcoTracker();
        tracker.update(List.of(sample(30.0f, 50.0f, 7.0)));
        tracker.update(List.of(sample(28.0f, 50.0f, 7.0)));
        tracker.update(List.of(sample(26.0f, 50.0f, 7.0)));
        tracker.update(List.of(sample(24.0f, 50.0f, 7.0)));
        tracker.update(List.of(sample(22.0f, 50.0f, 7.0)));
        tracker.update(List.of(sample(20.0f, 50.0f, 7.0)));
        tracker.update(List.of());

        assertEquals(1, tracker.stuckDeaths());
        assertEquals(100, tracker.lostGold());
        assertTrue(tracker.hasData());
    }

    @Test
    void playerDamageOnFinalStepDoesNotCount() {
        SlimeEcoTracker tracker = new SlimeEcoTracker();
        tracker.update(List.of(sample(30.0f, 50.0f, 7.0)));
        tracker.update(List.of(sample(28.0f, 50.0f, 7.0)));
        tracker.update(List.of(sample(26.0f, 50.0f, 7.0)));
        tracker.update(List.of(sample(24.0f, 50.0f, 7.0)));
        tracker.update(List.of(sample(22.0f, 50.0f, 7.0)));
        tracker.update(List.of(sample(18.0f, 50.0f, 7.0)));
        tracker.update(List.of());

        assertEquals(0, tracker.stuckDeaths());
        assertFalse(tracker.hasData());
    }

    @Test
    void enforcesDistanceGrowthAndUnloadBoundaries() {
        assertEquals(0, deathAtDistance(6.0));
        assertEquals(0, deathWithPeak(44.0f));
        assertEquals(0, deathWithHealthRemaining(22.0f));
        assertEquals(1, deathAtDistance(6.01));
    }

    @Test
    void sameMissingEntityIsSettledOnlyOnceAndResetClearsTotals() {
        SlimeEcoTracker tracker = new SlimeEcoTracker();
        for (float hp : new float[]{30.0f, 28.0f, 26.0f, 24.0f, 22.0f, 20.0f}) {
            tracker.update(List.of(sample(hp, 50.0f, 7.0)));
        }
        tracker.update(List.of());
        tracker.update(List.of());
        assertEquals(1, tracker.stuckDeaths());

        tracker.reset();
        assertEquals(0, tracker.stuckDeaths());
        assertEquals(0, tracker.lostGold());
        assertFalse(tracker.hasData());
    }

    private static SlimeEcoTracker.Sample sample(float health, float maxHealth, double distance) {
        return new SlimeEcoTracker.Sample(1, health, maxHealth, distance);
    }

    private static int deathAtDistance(double distance) {
        SlimeEcoTracker tracker = new SlimeEcoTracker();
        for (float hp : new float[]{30.0f, 28.0f, 26.0f, 24.0f, 22.0f, 20.0f}) {
            tracker.update(List.of(new SlimeEcoTracker.Sample(1, hp, 50.0f, distance)));
        }
        tracker.update(List.of());
        return tracker.stuckDeaths();
    }

    private static int deathWithPeak(float peak) {
        SlimeEcoTracker tracker = new SlimeEcoTracker();
        for (float hp : new float[]{30.0f, 28.0f, 26.0f, 24.0f, 22.0f, 20.0f}) {
            tracker.update(List.of(new SlimeEcoTracker.Sample(1, hp, peak, 7.0)));
        }
        tracker.update(List.of());
        return tracker.stuckDeaths();
    }

    private static int deathWithHealthRemaining(float health) {
        SlimeEcoTracker tracker = new SlimeEcoTracker();
        for (float hp : new float[]{30.0f, 28.0f, 26.0f, 24.0f, health}) {
            tracker.update(List.of(new SlimeEcoTracker.Sample(1, hp, 50.0f, 7.0)));
        }
        tracker.update(List.of());
        return tracker.stuckDeaths();
    }
}
