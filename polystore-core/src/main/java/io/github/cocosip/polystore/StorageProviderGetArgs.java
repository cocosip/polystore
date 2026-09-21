package io.github.cocosip.polystore;

/** Arguments for opening backend content. */
public final class StorageProviderGetArgs extends StorageProviderArgs {
    /** Creates get arguments. */
    public StorageProviderGetArgs(String containerName, ContainerConfiguration configuration, String fileId) {
        super(containerName, configuration, fileId);
    }
}
