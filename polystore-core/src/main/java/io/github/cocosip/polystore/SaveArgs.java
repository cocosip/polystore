package io.github.cocosip.polystore;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@link StorageClient#save(String, InputStream, SaveArgs)}.
 *
 * <p>Use {@link #defaults()} for standard behavior (overwrite enabled) or {@link #builder()} for
 * customization.</p>
 */
public final class SaveArgs {

    private final String contentType;
    private final Map<String, String> metadata;
    private final boolean overwrite;
    private final String tenantId;

    private SaveArgs(Builder builder) {
        this.contentType = builder.contentType;
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
        this.overwrite = builder.overwrite;
        this.tenantId = builder.tenantId;
    }

    /**
     * Returns arguments with default behavior: overwrite enabled, no content type, no metadata, no
     * explicit tenant id.
     *
     * @return default save arguments, never {@code null}
     */
    public static SaveArgs defaults() {
        return builder().build();
    }

    /**
     * Returns a new builder.
     *
     * @return builder, never {@code null}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the MIME type to store with the file, or {@code null} to let the backend decide.
     *
     * @return content type, may be {@code null}
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * Returns the custom metadata to store with the file, read-only.
     *
     * @return metadata, never {@code null}
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * Returns whether an existing file with the same name may be overwritten.
     *
     * @return {@code true} (default) to overwrite
     */
    public boolean isOverwrite() {
        return overwrite;
    }

    /**
     * Returns the explicit tenant id overriding the {@link TenantIdSupplier}, or {@code null} to
     * use the supplier. Useful for background jobs, cross-tenant administration and tests.
     *
     * @return explicit tenant id, may be {@code null}
     */
    public String getTenantId() {
        return tenantId;
    }

    @Override
    public String toString() {
        return "SaveArgs{contentType=" + contentType + ", metadata=" + metadata + ", overwrite=" + overwrite
                + ", tenantId=" + tenantId + "}";
    }

    /** Builder for {@link SaveArgs}. */
    public static final class Builder {

        private String contentType;
        private Map<String, String> metadata = new LinkedHashMap<>();
        private boolean overwrite = true;
        private String tenantId;

        private Builder() {}

        /**
         * Sets the MIME type stored with the file.
         *
         * @param contentType MIME type, may be {@code null}
         * @return this builder
         */
        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        /**
         * Replaces the custom metadata with a copy of the given map.
         *
         * @param metadata metadata entries, may be {@code null} to clear
         * @return this builder
         */
        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
            return this;
        }

        /**
         * Sets whether an existing file with the same name may be overwritten.
         *
         * @param overwrite default {@code true}
         * @return this builder
         */
        public Builder overwrite(boolean overwrite) {
            this.overwrite = overwrite;
            return this;
        }

        /**
         * Sets an explicit tenant id, taking priority over the {@link TenantIdSupplier}.
         *
         * @param tenantId tenant id, may be {@code null}
         * @return this builder
         */
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        /**
         * Builds the arguments.
         *
         * @return save arguments, never {@code null}
         */
        public SaveArgs build() {
            return new SaveArgs(this);
        }
    }
}
