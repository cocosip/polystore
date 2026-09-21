package io.github.cocosip.polystore;

/** Arguments for a backend existence check. */
public final class StorageProviderExistsArgs extends StorageProviderArgs {
    /** Creates existence arguments. */
    public StorageProviderExistsArgs(String containerName, ContainerConfiguration configuration, String fileId) {
        super(containerName, configuration, fileId);
    }
}
