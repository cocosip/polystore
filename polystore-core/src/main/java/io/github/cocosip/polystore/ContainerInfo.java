package io.github.cocosip.polystore;

/**
 * Read-only view of a container's identity: name, provider type and default flag. Provider
 * parameters are intentionally not exposed.
 */
public final class ContainerInfo {

    private final String name;
    private final String providerType;
    private final boolean isDefault;

    /**
     * Creates the metadata.
     *
     * @param name         container name
     * @param providerType provider type identifier
     * @param isDefault    whether the container is the default one
     */
    public ContainerInfo(String name, String providerType, boolean isDefault) {
        this.name = name;
        this.providerType = providerType;
        this.isDefault = isDefault;
    }

    /**
     * Creates the metadata from a container configuration.
     *
     * @param configuration container configuration, never {@code null}
     * @return metadata, never {@code null}
     */
    public static ContainerInfo from(ContainerConfiguration configuration) {
        return new ContainerInfo(configuration.getName(), configuration.getType(), configuration.isDefault());
    }

    /**
     * Returns the container name.
     *
     * @return container name, never {@code null}
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the provider type identifier.
     *
     * @return provider type identifier, never {@code null}
     */
    public String getProviderType() {
        return providerType;
    }

    /**
     * Returns whether this container is the default one.
     *
     * @return {@code true} if this container is the default one
     */
    public boolean isDefault() {
        return isDefault;
    }

    @Override
    public String toString() {
        return "ContainerInfo{name=" + name + ", providerType=" + providerType + ", isDefault=" + isDefault + "}";
    }
}
