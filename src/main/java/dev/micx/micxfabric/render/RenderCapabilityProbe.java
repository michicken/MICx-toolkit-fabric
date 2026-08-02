package dev.micx.micxfabric.render;

public final class RenderCapabilityProbe {
    private RenderCapabilityProbe() {
    }

    public static RenderCapabilities probe() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return new RenderCapabilities(
                os.contains("mac"),
                classPresent("org.lwjgl.vulkan.VK10"),
                classPresent("org.lwjgl.metal.MTLDevice"),
                false,
                false
        );
    }

    private static boolean classPresent(String name) {
        try {
            Class.forName(name, false, RenderCapabilityProbe.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
