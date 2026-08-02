package dev.micx.micxfabric;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/** Concurrent TeamSync snapshot store with explicit world/disable clearing. */
public final class TeamSyncState {
    private final ConcurrentHashMap<String, TeamSyncSnapshot> snapshots = new ConcurrentHashMap<>();

    public TeamSyncSnapshot getOrCreate(String name) {
        if (name == null || name.isBlank()) return null;
        return snapshots.computeIfAbsent(name, TeamSyncSnapshot::new);
    }

    public Collection<TeamSyncSnapshot> all() {
        return Collections.unmodifiableCollection(snapshots.values());
    }

    public void remove(String name) {
        if (name != null) snapshots.remove(name);
    }

    public void prune(java.util.function.Predicate<TeamSyncSnapshot> predicate) {
        if (predicate == null) return;
        snapshots.entrySet().removeIf(entry -> predicate.test(entry.getValue()));
    }

    public void clear() {
        snapshots.clear();
    }
}
