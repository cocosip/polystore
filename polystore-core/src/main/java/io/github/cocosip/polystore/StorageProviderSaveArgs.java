package io.github.cocosip.polystore;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable arguments for a backend save operation. */
public final class StorageProviderSaveArgs extends StorageProviderArgs {
    private final InputStream fileStream;
    private final long contentLength;
    private final String fileExt;
    private final boolean overrideExisting;
    private final String contentType;
    private final Map<String, String> metadata;

    /** Creates save arguments. The stream remains owned by the caller. */
    public StorageProviderSaveArgs(
            String containerName,
            ContainerConfiguration configuration,
            String fileId,
            InputStream fileStream,
            long contentLength,
            String fileExt,
            boolean overrideExisting,
            String contentType,
            Map<String, String> metadata) {
        super(containerName, configuration, fileId);
        this.fileStream = Objects.requireNonNull(fileStream, "fileStream");
        if (contentLength < 0) throw new IllegalArgumentException("contentLength must not be negative");
        this.contentLength = contentLength;
        this.fileExt = requireText(fileExt, "fileExt");
        this.overrideExisting = overrideExisting;
        this.contentType = contentType;
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata == null ? Map.of() : metadata));
    }

    /** Returns the caller-owned content stream. */
    public InputStream getFileStream() {
        return fileStream;
    }

    /** Returns the exact remaining byte count to consume. */
    public long getContentLength() {
        return contentLength;
    }

    /** Returns the file extension, including its leading dot. */
    public String getFileExt() {
        return fileExt;
    }

    /** Returns whether an existing file may be replaced. */
    public boolean isOverrideExisting() {
        return overrideExisting;
    }

    /** Returns the optional content type. */
    public String getContentType() {
        return contentType;
    }

    /** Returns read-only metadata. */
    public Map<String, String> getMetadata() {
        return metadata;
    }
}
