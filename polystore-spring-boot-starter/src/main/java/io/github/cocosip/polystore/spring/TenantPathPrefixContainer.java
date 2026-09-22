package io.github.cocosip.polystore.spring;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.ContainerInfo;
import io.github.cocosip.polystore.StorageContainer;
import io.github.cocosip.polystore.StorageSaveOptions;
import io.github.cocosip.polystore.TenantIdSupplier;
import io.github.cocosip.polystore.exception.TenantIdMissingException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;

/** Public container decorator that prefixes logical file identifiers with a tenant id. */
public final class TenantPathPrefixContainer implements StorageContainer {
    private final StorageContainer inner;
    private final TenantIdSupplier tenantIdSupplier;

    /**
     * Creates the tenant-isolating view.
     *
     * @param inner            decorated container, never {@code null}
     * @param tenantIdSupplier tenant id source, may be {@code null}
     */
    public TenantPathPrefixContainer(StorageContainer inner, TenantIdSupplier tenantIdSupplier) {
        this.inner = inner;
        this.tenantIdSupplier = tenantIdSupplier;
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
        String explicit = actual.getTenantId();
        String tenantId = explicit != null && !explicit.isBlank() ? explicit : requireSupplierTenantId();
        return inner.save(tenantId + "/" + fileId, stream, contentLength, ext, overrideExisting, actual);
    }

    @Override
    public boolean delete(String fileId) {
        return inner.delete(prefix(fileId));
    }

    @Override
    public boolean exists(String fileId) {
        return inner.exists(prefix(fileId));
    }

    @Override
    public boolean download(String fileId, Path path) {
        return inner.download(prefix(fileId), path);
    }

    @Override
    public InputStream getOrNull(String fileId) {
        return inner.getOrNull(prefix(fileId));
    }

    @Override
    public String getAccessUrl(String fileId, Instant expires, boolean checkFileExist) {
        return inner.getAccessUrl(prefix(fileId), expires, checkFileExist);
    }

    @Override
    public ContainerConfiguration getConfiguration() {
        return inner.getConfiguration();
    }

    @Override
    public String getName() {
        return inner.getName();
    }

    @Override
    public String getProviderType() {
        return inner.getProviderType();
    }

    @Override
    public ContainerInfo getInfo() {
        return inner.getInfo();
    }

    private String prefix(String fileId) {
        return requireSupplierTenantId() + "/" + fileId;
    }

    private String requireSupplierTenantId() {
        if (tenantIdSupplier == null) throw new TenantIdMissingException(inner.getName());
        String tenantId = tenantIdSupplier.get();
        if (tenantId == null || tenantId.isBlank()) throw new TenantIdMissingException(inner.getName());
        return tenantId;
    }
}
