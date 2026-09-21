package io.github.cocosip.polystore;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.InputStream;
import java.util.Collection;

/**
 * Straightforward {@link StorageContainer} implementation delegating every file operation to a
 * backend-specific {@link StorageClient}. Storage backends (which depend only on this module) use
 * it inside their {@link StorageProvider#createContainer(ContainerConfiguration)} implementations.
 */
public class DefaultStorageContainer implements StorageContainer {

    private final String name;
    private final String providerType;
    private final ContainerInfo info;
    private final StorageClient delegate;

    /** Creates a non-default container whose info is derived from name and provider type. */
    public DefaultStorageContainer(String name, String providerType, StorageClient delegate) {
        this(name, providerType, new ContainerInfo(name, providerType, false), delegate);
    }

    /** Creates a container with an explicit default flag in its info. */
    public DefaultStorageContainer(String name, String providerType, boolean isDefault, StorageClient delegate) {
        this(name, providerType, new ContainerInfo(name, providerType, isDefault), delegate);
    }

    /** Creates a container backed by the given client, exposing the given metadata. */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "delegation is the purpose of this class; the client is shared by design")
    public DefaultStorageContainer(String name, String providerType, ContainerInfo info, StorageClient delegate) {
        this.name = name;
        this.providerType = providerType;
        this.info = info;
        this.delegate = delegate;
    }

    /**
     * Creates a container from a container configuration.
     *
     * @param configuration container configuration providing name, type and default flag
     * @param client        backend-specific file operations
     * @return container, never {@code null}
     */
    public static DefaultStorageContainer from(ContainerConfiguration configuration, StorageClient client) {
        return new DefaultStorageContainer(
                configuration.getName(), configuration.getType(), ContainerInfo.from(configuration), client);
    }

    @Override
    public void save(String fileName, InputStream inputStream, SaveArgs args) {
        delegate.save(fileName, inputStream, args);
    }

    @Override
    public InputStream get(String fileName) {
        return delegate.get(fileName);
    }

    @Override
    public void delete(String fileName) {
        delegate.delete(fileName);
    }

    @Override
    public boolean exists(String fileName) {
        return delegate.exists(fileName);
    }

    @Override
    public String getUrl(String fileName, UrlArgs args) {
        return delegate.getUrl(fileName, args);
    }

    @Override
    public void deleteAll(Collection<String> fileNames) {
        delegate.deleteAll(fileNames);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getProviderType() {
        return providerType;
    }

    @Override
    public ContainerInfo getInfo() {
        return info;
    }
}
