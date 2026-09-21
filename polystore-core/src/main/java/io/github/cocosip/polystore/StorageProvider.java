package io.github.cocosip.polystore;

import io.github.cocosip.polystore.exception.StorageOperationException;
import java.util.Collection;
import java.util.List;

/**
 * Service provider interface (SPI) implemented by every storage backend.
 *
 * <p>Providers are registered by type identifier; a {@link StorageManager} looks a provider up by
 * the {@code type} field of each {@link ContainerConfiguration} and asks it to create the container
 * instance. Every registered type and alias is matched case-insensitively.</p>
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
     * Returns additional type identifiers this provider answers to, so configurations written
     * against another naming scheme keep working — for example the provider name of the C#
     * reference framework ({@code FileSystem} for {@code local}, {@code Aliyun} for
     * {@code aliyun-oss}, {@code Obs} for {@code huawei-obs}). A canonical
     * {@link #getType()} registration always wins over an alias of another provider.
     *
     * @return alias type identifiers, never {@code null}; empty by default
     */
    default Collection<String> getAliases() {
        return List.of();
    }

    /**
     * Creates a backend bound to the given configuration.
     *
     * @param config container configuration, never {@code null}
     * @return a ready-to-use backend, never {@code null}
     * @throws StorageOperationException if the backend client cannot be initialized
     */
    StorageBackend createBackend(ContainerConfiguration config);
}
