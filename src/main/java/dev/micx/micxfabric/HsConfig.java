package dev.micx.micxfabric;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Properties;

/** Fabric configuration for the HS presence and bot dispatch service. */
final class HsConfig {
    static final String DEFAULT_TOKEN_URL = "https://zombie.nienie.fun/zombies/token";
    static final String DEFAULT_PRESENCE_URL = "https://zombie.nienie.fun/zombies/hs/presence";
    static final String DEFAULT_JOB_URL = "https://zombie.nienie.fun/zombies/hs/jobs";

    boolean enabled = true;
    String tokenUrl = DEFAULT_TOKEN_URL;
    String presenceUrl = DEFAULT_PRESENCE_URL;
    String jobUrl = DEFAULT_JOB_URL;
    int presenceIntervalSeconds = 30;
    private boolean loaded;

    void load() {
        if (loaded) return;
        loaded = true;
        Path current = FabricRuntime.configPath().resolve("hs.properties");
        Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_Hs.cfg");
        Properties properties = ConfigProperties.load(current, legacy);
        enabled = ConfigProperties.bool(properties, "enabled", true);
        tokenUrl = safeHttps(ConfigProperties.string(properties, "tokenUrl", DEFAULT_TOKEN_URL),
                DEFAULT_TOKEN_URL);
        presenceUrl = safeHttps(ConfigProperties.string(properties, "presenceUrl", DEFAULT_PRESENCE_URL),
                DEFAULT_PRESENCE_URL);
        jobUrl = safeJobUrl(ConfigProperties.string(properties, "jobUrl", DEFAULT_JOB_URL));
        presenceIntervalSeconds = ConfigProperties.integer(properties, "presenceIntervalSeconds", 30, 10, 120);
    }

    void save() {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("tokenUrl", safeHttps(tokenUrl, DEFAULT_TOKEN_URL));
        properties.setProperty("presenceUrl", safeHttps(presenceUrl, DEFAULT_PRESENCE_URL));
        properties.setProperty("jobUrl", safeJobUrl(jobUrl));
        properties.setProperty("presenceIntervalSeconds", Integer.toString(
                Math.max(10, Math.min(120, presenceIntervalSeconds))));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("hs.properties"), properties,
                    "MICx HS configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save HS configuration", exception);
        }
    }

    static String deriveEndpoint(String jobUrl, String suffix) {
        try {
            URI uri = URI.create(jobUrl == null ? "" : jobUrl.trim());
            String path = uri.getPath();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                    || path == null || !path.endsWith("/hs/jobs")) return "";
            URI derived = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(),
                    path.substring(0, path.length() - "/hs/jobs".length()) + "/hs/" + suffix,
                    null, null);
            return derived.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String safeJobUrl(String value) {
        String safe = safeHttps(value, DEFAULT_JOB_URL);
        return deriveEndpoint(safe, "dispatch").isBlank() ? DEFAULT_JOB_URL : safe;
    }

    private static String safeHttps(String value, String fallback) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || uri.getPath() == null
                    || uri.getPath().isBlank() || (uri.getPort() != -1 && uri.getPort() != 443)) {
                return fallback;
            }
            return uri.toString();
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
