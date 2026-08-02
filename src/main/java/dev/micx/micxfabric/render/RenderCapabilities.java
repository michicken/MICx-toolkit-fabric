package dev.micx.micxfabric.render;

public record RenderCapabilities(
        boolean macOs,
        boolean lwjglVulkanPresent,
        boolean lwjglMetalPresent,
        boolean nativeMetalPresent,
        boolean nativeVulkanPresent
) {
    public boolean hasMetalApiClasses() {
        return macOs && (lwjglMetalPresent || nativeMetalPresent);
    }

    public boolean hasVulkanApiClasses() {
        return lwjglVulkanPresent || nativeVulkanPresent;
    }
}
