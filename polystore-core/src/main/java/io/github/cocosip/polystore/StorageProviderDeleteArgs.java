package io.github.cocosip.polystore;

/** Arguments for a backend delete operation. */
public final class StorageProviderDeleteArgs extends StorageProviderArgs {
    /** Creates delete arguments. */
    public StorageProviderDeleteArgs(String containerName, ContainerConfiguration configuration, String fileId) {
        super(containerName, configuration, fileId);
    }
}
