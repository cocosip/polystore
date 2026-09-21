package io.github.cocosip.polystore.spring;

import io.github.cocosip.polystore.ContainerInfo;
import io.github.cocosip.polystore.SaveArgs;
import io.github.cocosip.polystore.StorageContainer;
import io.github.cocosip.polystore.TenantIdSupplier;
import io.github.cocosip.polystore.TenantIsolationMode;
import io.github.cocosip.polystore.UrlArgs;
import io.github.cocosip.polystore.exception.TenantIdMissingException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Wraps a container configured with {@link TenantIsolationMode#PATH_PREFIX} and transparently
 * prefixes every file path with the resolved tenant id: {@code images/photo.jpg} becomes
 * {@code {tenantId}/images/photo.jpg}.
 *
 * <p>Tenant id resolution priority: {@link SaveArgs#getTenantId()} (explicit) &gt;
 * {@link TenantIdSupplier} (implicit) &gt; missing. A missing id raises
 * {@link TenantIdMissingException}.</p>
 */
public final class TenantPathPrefixContainer implements StorageContainer {

    private final StorageContainer inner;
    private final TenantIdSupplier tenantIdSupplier;

    /**
     * Creates the isolating view.
     *
     * @param inner            the container to wrap, never {@code null}
     * @param tenantIdSupplier tenant id source, may be {@code null} (then only explicit
     *                         {@code SaveArgs.tenantId} can satisfy PATH_PREFIX)
     */
    public TenantPathPrefixContainer(StorageContainer inner, TenantIdSupplier tenantIdSupplier) {
        this.inner = inner;
        this.tenantIdSupplier = tenantIdSupplier;
    }

    @Override
    public void save(String fileName, InputStream inputStream, SaveArgs args) {
        String explicit = args.getTenantId();
        String tenantId = explicit != null && !explicit.trim().isEmpty() ? explicit : requireSupplierTenantId();
        inner.save(tenantId + "/" + fileName, inputStream, args);
    }

    @Override
    public InputStream get(String fileName) {
        return inner.get(prefix(fileName));
    }

    @Override
    public void delete(String fileName) {
        inner.delete(prefix(fileName));
    }

    @Override
    public boolean exists(String fileName) {
        return inner.exists(prefix(fileName));
    }

    @Override
    public String getUrl(String fileName, UrlArgs args) {
        return inner.getUrl(prefix(fileName), args);
    }

    @Override
    public void deleteAll(Collection<String> fileNames) {
        List<String> prefixed = new ArrayList<>(fileNames.size());
        for (String fileName : fileNames) {
            prefixed.add(prefix(fileName));
        }
        inner.deleteAll(prefixed);
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

    private String prefix(String fileName) {
        return requireSupplierTenantId() + "/" + fileName;
    }

    private String requireSupplierTenantId() {
        if (tenantIdSupplier == null) {
            throw new TenantIdMissingException(inner.getName());
        }
        String tenantId = tenantIdSupplier.get();
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new TenantIdMissingException(inner.getName());
        }
        return tenantId;
    }
}
