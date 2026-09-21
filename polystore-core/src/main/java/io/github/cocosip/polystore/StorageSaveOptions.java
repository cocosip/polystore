package io.github.cocosip.polystore;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable Polystore-specific options for a save operation. */
public final class StorageSaveOptions {
    private static final StorageSaveOptions DEFAULTS = builder().build();

    private final String contentType;
    private final Map<String, String> metadata;
    private final String tenantId;

    private StorageSaveOptions(Builder builder) {
        contentType = builder.contentType;
        tenantId = builder.tenantId;
        metadata = Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
    }

    /** Returns the default empty options. */
    public static StorageSaveOptions defaults() {
        return DEFAULTS;
    }

    /** Returns a new builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Returns the optional content type. */
    public String getContentType() {
        return contentType;
    }

    /** Returns read-only object metadata. */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /** Returns the optional explicit tenant identifier. */
    public String getTenantId() {
        return tenantId;
    }

    /** Builder for {@link StorageSaveOptions}. */
    public static final class Builder {
        private String contentType;
        private Map<String, String> metadata = new LinkedHashMap<>();
        private String tenantId;

        private Builder() {}

        /** Sets the optional content type. */
        public Builder contentType(String value) {
            contentType = value;
            return this;
        }

        /** Replaces metadata with a defensive copy. */
        public Builder metadata(Map<String, String> value) {
            metadata = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
            return this;
        }

        /** Sets the optional explicit tenant identifier. */
        public Builder tenantId(String value) {
            tenantId = value;
            return this;
        }

        /** Builds the immutable options. */
        public StorageSaveOptions build() {
            return new StorageSaveOptions(this);
        }
    }
}
