package io.github.cocosip.polystore;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/** Default public container coordinator that converts calls into backend argument objects. */
public class DefaultStorageContainer implements StorageContainer {
    private final ContainerConfiguration configuration;
    private final ContainerInfo info;
    private final StorageBackend backend;

    /**
     * Creates a container from immutable configuration and a backend implementation.
     *
     * @param configuration container configuration, never {@code null}
     * @param backend       backend implementation, never {@code null}
     */
    public DefaultStorageContainer(ContainerConfiguration configuration, StorageBackend backend) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.info = ContainerInfo.from(configuration);
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    /**
     * Creates a default container coordinator.
     *
     * @param configuration container configuration, never {@code null}
     * @param backend       backend implementation, never {@code null}
     * @return container coordinator, never {@code null}
     */
    public static DefaultStorageContainer from(ContainerConfiguration configuration, StorageBackend backend) {
        return new DefaultStorageContainer(configuration, backend);
    }

    @Override
    public String save(
            String fileId,
            InputStream stream,
            long contentLength,
            String ext,
            boolean overrideExisting,
            StorageSaveOptions options) {
        StorageSaveOptions actual = options == null ? StorageSaveOptions.defaults() : options;
        return backend.save(new StorageProviderSaveArgs(
                getName(),
                configuration,
                fileId,
                stream,
                contentLength,
                ext,
                overrideExisting,
                actual.getContentType(),
                actual.getMetadata()));
    }

    @Override
    public boolean delete(String fileId) {
        return backend.delete(new StorageProviderDeleteArgs(getName(), configuration, fileId));
    }

    @Override
    public boolean exists(String fileId) {
        return backend.exists(new StorageProviderExistsArgs(getName(), configuration, fileId));
    }

    @Override
    public boolean download(String fileId, Path path) {
        return backend.download(new StorageProviderDownloadArgs(getName(), configuration, fileId, path));
    }

    @Override
    public InputStream getOrNull(String fileId) {
        return backend.getOrNull(new StorageProviderGetArgs(getName(), configuration, fileId));
    }

    @Override
    public String getAccessUrl(String fileId, Instant expires, boolean checkFileExist) {
        if (!configuration.isHttpAccess()) {
            return "";
        }
        return backend.getAccessUrl(
                new StorageProviderAccessArgs(getName(), configuration, fileId, expires, checkFileExist));
    }

    @Override
    public ContainerConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public String getName() {
        return configuration.getName();
    }

    @Override
    public String getProviderType() {
        return configuration.getType();
    }

    @Override
    public ContainerInfo getInfo() {
        return info;
    }
}
