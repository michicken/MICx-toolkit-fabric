package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;

/** Read-only descriptor backing an original module that has no Fabric runtime yet. */
public final class UnmigratedModule implements Module {
    private final String id;

    public UnmigratedModule(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public void setEnabled(boolean enabled) {
        // Intentionally read-only until the actual Fabric runtime is migrated.
    }

    @Override
    public void tick(Minecraft client) {
        // No runtime behavior is claimed by this placeholder.
    }
}
