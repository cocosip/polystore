package io.github.cocosip.polystore;

import java.time.Instant;
import java.util.Objects;

/** Arguments for resolving a backend access URL. */
public final class StorageProviderAccessArgs extends StorageProviderArgs {
    private final Instant expires;
    private final boolean checkFileExist;

    /** Creates access arguments. */
    public StorageProviderAccessArgs(
            String containerName,
            ContainerConfiguration configuration,
            String fileId,
            Instant expires,
            boolean checkFileExist) {
        super(containerName, configuration, fileId);
        this.expires = Objects.requireNonNull(expires, "expires");
        this.checkFileExist = checkFileExist;
    }

    /** Returns the absolute URL expiration time. */
    public Instant getExpires() {
        return expires;
    }

    /** Returns whether existence must be checked first. */
    public boolean isCheckFileExist() {
        return checkFileExist;
    }
}
