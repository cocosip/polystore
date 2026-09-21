package io.github.cocosip.polystore.local;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.util.ConfigUtils;

/**
 * Immutable parameters of one {@code local} container.
 *
 * @param basePath                        storage root directory, required
 * @param appendContainerNameToBasePath   whether files are stored under a {@code containerName}
 *                                        sub-directory, default {@code true}
 * @param httpServer                      HTTP static-resource server prefixed to the URL returned
 *                                        by {@code getUrl}, default empty
 * @param createDirectories               whether the missing base directory is created, default
 *                                        {@code true}
 */
record LocalStorageConfiguration(
        String basePath, boolean appendContainerNameToBasePath, String httpServer, boolean createDirectories) {

    /**
     * Parses and validates the parameters of one {@code local} container.
     *
     * @param config container configuration holding the provider parameters
     * @return parsed configuration, never {@code null}
     * @throws IllegalStateException if a required parameter is missing
     */
    static LocalStorageConfiguration from(ContainerConfiguration config) {
        var properties = config.getProperties();
        String basePath = ConfigUtils.requireString(properties, "basePath");
        boolean appendContainerNameToBasePath =
                ConfigUtils.optBoolean(properties, "appendContainerNameToBasePath", true);
        String httpServer = ConfigUtils.optString(properties, "httpServer", "");
        boolean createDirectories = ConfigUtils.optBoolean(properties, "createDirectories", true);
        return new LocalStorageConfiguration(basePath, appendContainerNameToBasePath, httpServer, createDirectories);
    }
}
