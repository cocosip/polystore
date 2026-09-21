package io.github.cocosip.polystore.util;

import java.util.Map;

/**
 * Lookup helpers for provider-specific parameters stored in
 * {@link io.github.cocosip.polystore.ContainerConfiguration#getProperties()}.
 *
 * <p>Keys arriving from yml are kebab-case ({@code access-key}) while programmatic builders
 * naturally use camelCase ({@code accessKey}); lookups are case-insensitive and
 * separator-insensitive so both forms work. The fully qualified key style of the C# reference
 * framework ({@code Minio.EndPoint}, {@code Aws.Region}, ...) is also accepted: a stored key
 * matches when it equals the requested key or when its last dot-separated segment does. Missing
 * required parameters raise
 * {@link IllegalStateException} — a configuration problem, not a storage operation failure.</p>
 */
public final class ConfigUtils {

    private ConfigUtils() {}

    /**
     * Normalizes a key for matching: lowercased with {@code -} and {@code _} removed, so
     * {@code access-key}, {@code accessKey} and {@code ACCESS_KEY} all normalize to
     * {@code accesskey}.
     *
     * @param key key to normalize, may be {@code null}
     * @return normalized key, or {@code null} if the input was null
     */
    public static String normalizeKey(String key) {
        if (key == null) {
            return null;
        }
        StringBuilder normalized = new StringBuilder(key.length());
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '-' || c == '_') {
                continue;
            }
            normalized.append(Character.toLowerCase(c));
        }
        return normalized.toString();
    }

    /**
     * Returns the raw value for the given key, matching keys regardless of case and separator
     * style. A stored key of the reference framework's qualified form ({@code Minio.EndPoint})
     * also matches its plain logical key ({@code endPoint}).
     *
     * @param properties provider parameters, never {@code null}
     * @param key        logical key, e.g. {@code accessKey} or {@code access-key}
     * @return value, or {@code null} if absent
     */
    public static Object get(Map<String, Object> properties, String key) {
        String normalized = normalizeKey(key);
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (matches(normalized, entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static boolean matches(String normalizedKey, String storedKey) {
        String normalizedStored = normalizeKey(storedKey);
        if (normalizedStored == null) {
            return false;
        }
        if (normalizedStored.equals(normalizedKey)) {
            return true;
        }
        int lastDot = normalizedStored.lastIndexOf('.');
        return lastDot >= 0 && normalizedStored.substring(lastDot + 1).equals(normalizedKey);
    }

    /**
     * Returns the required string parameter.
     *
     * @param properties provider parameters
     * @param key        logical key
     * @return value as string, never blank
     * @throws IllegalStateException if the parameter is missing or blank
     */
    public static String requireString(Map<String, Object> properties, String key) {
        String value = asString(get(properties, key));
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Missing required storage parameter '" + key + "'");
        }
        return value.trim();
    }

    /**
     * Returns the optional string parameter.
     *
     * @param properties   provider parameters
     * @param key          logical key
     * @param defaultValue fallback when absent or blank
     * @return value as string, never {@code null}
     */
    public static String optString(Map<String, Object> properties, String key, String defaultValue) {
        String value = asString(get(properties, key));
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }

    /**
     * Returns the optional int parameter.
     *
     * @param properties   provider parameters
     * @param key          logical key
     * @param defaultValue fallback when absent or unparsable
     * @return value as int
     */
    public static int optInt(Map<String, Object> properties, String key, int defaultValue) {
        Object value = get(properties, key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.trim().isEmpty()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * Returns the optional long parameter.
     *
     * @param properties   provider parameters
     * @param key          logical key
     * @param defaultValue fallback when absent or unparsable
     * @return value as long
     */
    public static long optLong(Map<String, Object> properties, String key, long defaultValue) {
        Object value = get(properties, key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.trim().isEmpty()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * Returns the optional boolean parameter.
     *
     * @param properties   provider parameters
     * @param key          logical key
     * @param defaultValue fallback when absent
     * @return value as boolean
     */
    public static boolean optBoolean(Map<String, Object> properties, String key, boolean defaultValue) {
        Object value = get(properties, key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.trim().isEmpty()) {
            return Boolean.parseBoolean(text.trim());
        }
        return defaultValue;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
