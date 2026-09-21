package io.github.cocosip.polystore;

import java.time.Duration;

/**
 * Parameters for {@link StorageClient#getUrl(String, UrlArgs)}.
 *
 * <p>Use {@link #defaults()} for standard behavior (one hour expiry, attachment disposition) or
 * {@link #builder()} for customization.</p>
 */
public final class UrlArgs {

    /** Default presigned URL expiry: one hour. */
    public static final Duration DEFAULT_EXPIRY = Duration.ofHours(1);

    private final Duration expiry;
    private final boolean inline;

    private UrlArgs(Builder builder) {
        this.expiry = builder.expiry;
        this.inline = builder.inline;
    }

    /**
     * Returns arguments with default behavior: one hour expiry, attachment disposition.
     *
     * @return default URL arguments, never {@code null}
     */
    public static UrlArgs defaults() {
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
     * Returns the presigned URL expiry for backends that generate signed URLs; ignored by backends
     * with unsigned access paths.
     *
     * @return expiry, never {@code null}
     */
    public Duration getExpiry() {
        return expiry;
    }

    /**
     * Returns whether the URL should serve the file inline in the browser instead of as a
     * download attachment.
     *
     * @return {@code true} for inline disposition
     */
    public boolean isInline() {
        return inline;
    }

    @Override
    public String toString() {
        return "UrlArgs{expiry=" + expiry + ", inline=" + inline + "}";
    }

    /** Builder for {@link UrlArgs}. */
    public static final class Builder {

        private Duration expiry = DEFAULT_EXPIRY;
        private boolean inline;

        private Builder() {}

        /**
         * Sets the presigned URL expiry.
         *
         * @param expiry must not be {@code null} or non-positive
         * @return this builder
         */
        public Builder expiry(Duration expiry) {
            this.expiry = expiry;
            return this;
        }

        /**
         * Sets whether the file should be served inline in the browser instead of as a download
         * attachment.
         *
         * @param inline default {@code false}
         * @return this builder
         */
        public Builder inline(boolean inline) {
            this.inline = inline;
            return this;
        }

        /**
         * Builds the arguments.
         *
         * @return URL arguments, never {@code null}
         */
        public UrlArgs build() {
            return new UrlArgs(this);
        }
    }
}
