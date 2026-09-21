package io.github.cocosip.polystore;

import io.github.cocosip.polystore.exception.StorageOperationException;

/**
 * Service provider interface (SPI) implemented by every storage backend.
 *
 * <p>Providers are registered by type identifier; a {@link StorageManager} looks a provider up by
 * the {@code type} field of each {@link ContainerConfiguration} and asks it to create the container
 * instance.</p>
 */
public interface StorageProvider {

    /**
     * Returns the provider type identifier, matching the {@code type} field in the container
     * configuration, e.g. {@code "minio"}.
     *
     * @return provider type identifier, never {@code null}
     */
    String getType();

    /**
     * Creates a container bound to the given configuration.
     *
     * @param config container configuration, never {@code null}
     * @return a ready-to-use container, never {@code null}
     * @throws StorageOperationException if the backend client cannot be initialized
     */
    StorageContainer createContainer(ContainerConfiguration config);
}
