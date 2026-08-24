package dev.micx.micxfabric;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Small compatibility loader for the old flat .cfg files and Fabric properties. */
public final class ConfigProperties {
    private ConfigProperties() {
    }

    public static Properties load(Path current, Path legacy) {
        Properties properties = new Properties();
        if (current != null && Files.isRegularFile(current)) {
            try (InputStream input = Files.newInputStream(current)) {
                properties.load(input);
            } catch (IOException exception) {
                MicxFabric.LOGGER.warn("Unable to load MICx configuration", exception);
            }
            return properties;
        }
        if (legacy == null || !Files.isRegularFile(legacy)) return properties;
        try (BufferedReader reader = Files.newBufferedReader(legacy, StandardCharsets.UTF_8)) {
            parseLegacy(reader, properties);
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to load MICx legacy configuration", exception);
        }
        return properties;
    }

    private static void parseLegacy(BufferedReader reader, Properties properties) throws IOException {
        String category = "";
        String line;
        while ((line = reader.readLine()) != null) {
            String value = line.trim();
            if (value.isEmpty() || value.startsWith("#") || value.startsWith("//")) continue;
            if (value.endsWith("{") && value.indexOf('=') < 0) {
                category = value.substring(0, value.length() - 1).trim();
                continue;
            }
            if (value.equals("}")) {
                category = "";
                continue;
            }
            int equals = value.indexOf('=');
            if (equals <= 0) continue;
            String key = value.substring(0, equals).trim();
            String parsed = value.substring(equals + 1).trim();
            if (key.length() > 2 && key.charAt(1) == ':') {
                String type = key.substring(0, 2);
                String name = key.substring(2).trim();
                if (!name.isEmpty()) {
                    properties.setProperty(type + name, parsed);
                    if (!category.isEmpty()) properties.setProperty(category + "." + name, parsed);
                    properties.setProperty(name, parsed);
                }
            } else if (!key.isEmpty()) {
                if (!category.isEmpty()) properties.setProperty(category + "." + key, parsed);
                properties.setProperty(key, parsed);
            }
        }
    }

    public static boolean bool(Properties properties, String key, boolean fallback) {
        String value = value(properties, key);
        if (value == null) return fallback;
        if ("true".equalsIgnoreCase(value.trim())) return true;
        if ("false".equalsIgnoreCase(value.trim())) return false;
        return fallback;
    }

    public static String string(Properties properties, String key, String fallback) {
        String value = value(properties, key);
        return value == null ? fallback : value.trim();
    }

    public static int integer(Properties properties, String key, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value(properties, key).trim())));
        } catch (Exception ignored) {
            return Math.max(min, Math.min(max, fallback));
        }
    }

    public static double real(Properties properties, String key, double fallback, double min, double max) {
        try {
            return Math.max(min, Math.min(max, Double.parseDouble(value(properties, key).trim())));
        } catch (Exception ignored) {
            return Math.max(min, Math.min(max, fallback));
        }
    }

    private static String value(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            for (String candidate : properties.stringPropertyNames()) {
                if (candidate.equals(key) || candidate.endsWith("." + key)) {
                    value = properties.getProperty(candidate);
                    break;
                }
            }
        }
        if (value == null) {
            for (String prefix : new String[]{"B:", "I:", "S:"}) {
                value = properties.getProperty(prefix + key);
                if (value != null) break;
            }
        }
        if (value == null) return null;
        int colon = value.indexOf(':');
        if (colon == 1 && (value.charAt(0) == 'B' || value.charAt(0) == 'I' || value.charAt(0) == 'S')) {
            return value.substring(2).trim();
        }
        return value;
    }
}
