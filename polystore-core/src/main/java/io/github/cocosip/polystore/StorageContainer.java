package io.github.cocosip.polystore;

/**
 * A named storage container: a {@link StorageClient} bound to one backend configuration.
 *
 * <p>Container names are unique within a {@link StorageManager}. Several containers may coexist in
 * the same process, each backed by a different provider type.</p>
 */
public interface StorageContainer extends StorageClient {

    /**
     * Returns this container's complete immutable configuration.
     *
     * @return container configuration, never {@code null}
     */
    ContainerConfiguration getConfiguration();

    /**
     * Returns the container name, unique within its {@link StorageManager}.
     *
     * @return container name, never {@code null}
     */
    String getName();

    /**
     * Returns the backend provider type identifier, e.g. {@code "local"}, {@code "minio"},
     * {@code "s3"}.
     *
     * @return provider type identifier, never {@code null}
     */
    String getProviderType();

    /**
     * Returns a read-only view of the container metadata.
     *
     * @return container metadata, never {@code null}
     */
    ContainerInfo getInfo();
}
