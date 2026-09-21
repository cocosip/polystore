package io.github.cocosip.polystore;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;

/** Common immutable arguments supplied to a {@link StorageBackend}. */
public abstract class StorageProviderArgs {
    private final String containerName;
    private final ContainerConfiguration configuration;
    private final String fileId;

    @SuppressFBWarnings(
            value = "CT_CONSTRUCTOR_THROW",
            justification = "immutable operation arguments must reject invalid required values during construction")
    protected StorageProviderArgs(String containerName, ContainerConfiguration configuration, String fileId) {
        this.containerName = requireText(containerName, "containerName");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.fileId = requireText(fileId, "fileId");
    }

    /** Returns the configured container name. */
    public String getContainerName() {
        return containerName;
    }

    /** Returns the complete fixed and provider-specific configuration. */
    public ContainerConfiguration getConfiguration() {
        return configuration;
    }

    /** Returns the logical file identifier. */
    public String getFileId() {
        return fileId;
    }

    static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
