package dev.micx.micxfabric;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/** 新手引导完成标记（config/MICxToolkit/onboarding.properties）。 */
public final class StarterGuideState {
    private static final String FILE = "onboarding.properties";
    private static volatile Boolean completed;

    private StarterGuideState() {
    }

    public static boolean isCompleted() {
        Boolean value = completed;
        if (value != null) return value;
        Properties properties = ConfigProperties.load(FabricRuntime.configPath().resolve(FILE), null);
        value = "1".equals(properties.getProperty("guide_completed", "0"));
        completed = value;
        return value;
    }

    public static void markCompleted() {
        Path file = FabricRuntime.configPath().resolve(FILE);
        Properties properties = new Properties();
        properties.setProperty("guide_completed", "1");
        try {
            AtomicProperties.store(file, properties, "MICx Toolkit onboarding state");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save onboarding state", exception);
        }
        completed = true;
    }

    public static void resetForTesting() {
        completed = null;
    }
}
