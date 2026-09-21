package io.github.cocosip.polystore;

import java.nio.file.Path;
import java.util.Objects;

/** Arguments for downloading backend content to a path. */
public final class StorageProviderDownloadArgs extends StorageProviderArgs {
    private final Path path;

    /** Creates download arguments. */
    public StorageProviderDownloadArgs(
            String containerName, ContainerConfiguration configuration, String fileId, Path path) {
        super(containerName, configuration, fileId);
        this.path = Objects.requireNonNull(path, "path");
    }

    /** Returns the destination path. */
    public Path getPath() {
        return path;
    }
}
