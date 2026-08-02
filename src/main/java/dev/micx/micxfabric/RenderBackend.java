package dev.micx.micxfabric;

public enum RenderBackend {
    VANILLA_FABRIC,
    VULKAN_EXPERIMENTAL,
    METAL_EXPERIMENTAL;

    public static RenderBackend current() {
        String value = System.getProperty("micx.render.backend", "vanilla");
        if ("metal".equalsIgnoreCase(value)) return METAL_EXPERIMENTAL;
        if ("vulkan".equalsIgnoreCase(value) || "moltenvk".equalsIgnoreCase(value)) {
            return VULKAN_EXPERIMENTAL;
        }
        return VANILLA_FABRIC;
    }
}
