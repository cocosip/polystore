package io.github.cocosip.polystore;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration for one container, mirroring one entry of the {@code polystore.containers} list.
 *
 * <p>Provider-specific parameters (endpoint, credentials, bucket name, ...) live in
 * {@link #getProperties()}; their shape is defined by each {@link StorageProvider}.</p>
 */
public final class ContainerConfiguration {

    private final String name;
    private final String type;
    private final boolean isDefault;
    private final TenantIsolationMode tenantIsolation;
    private final Map<String, Object> properties;

    private ContainerConfiguration(Builder builder) {
        this.name = builder.name;
        this.type = builder.type;
        this.isDefault = builder.isDefault;
        this.tenantIsolation = builder.tenantIsolation;
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(builder.properties));
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
     * Returns the container name, unique within a {@link StorageManager}.
     *
     * @return container name, never {@code null}
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the provider type identifier, matching {@link StorageProvider#getType()}.
     *
     * @return provider type identifier, never {@code null}
     */
    public String getType() {
        return type;
    }

    /**
     * Returns whether this container is the default one.
     *
     * @return {@code true} if this container answers {@link StorageManager#getDefaultContainer()}
     */
    public boolean isDefault() {
        return isDefault;
    }

    /**
     * Returns the tenant isolation mode of this container.
     *
     * @return isolation mode, never {@code null}
     */
    public TenantIsolationMode getTenantIsolation() {
        return tenantIsolation;
    }

    /**
     * Returns the provider-specific parameters, read-only.
     *
     * @return provider parameters, never {@code null}
     */
    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * Returns the provider parameter for the given key.
     *
     * @param key parameter key
     * @return parameter value, or {@code null} if absent
     */
    public Object getProperty(String key) {
        return properties.get(key);
    }

    @Override
    public String toString() {
        return "ContainerConfiguration{name=" + name + ", type=" + type + ", isDefault=" + isDefault
                + ", tenantIsolation=" + tenantIsolation + ", properties=" + properties + "}";
    }

    /** Builder for {@link ContainerConfiguration}. */
    public static final class Builder {

        private String name;
        private String type;
        private boolean isDefault;
        private TenantIsolationMode tenantIsolation = TenantIsolationMode.NONE;
        private Map<String, Object> properties = new LinkedHashMap<>();

        private Builder() {}

        /**
         * Sets the container name.
         *
         * @param name container name
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the provider type identifier.
         *
         * @param type provider type identifier, e.g. {@code "minio"}
         * @return this builder
         */
        public Builder type(String type) {
            this.type = type;
            return this;
        }

        /**
         * Sets whether this container is the default one.
         *
         * @param isDefault default {@code false}
         * @return this builder
         */
        public Builder isDefault(boolean isDefault) {
            this.isDefault = isDefault;
            return this;
        }

        /**
         * Sets the tenant isolation mode.
         *
         * @param tenantIsolation default {@link TenantIsolationMode#NONE}
         * @return this builder
         */
        public Builder tenantIsolation(TenantIsolationMode tenantIsolation) {
            this.tenantIsolation = tenantIsolation;
            return this;
        }

        /**
         * Replaces the provider parameters with a copy of the given map.
         *
         * @param properties provider parameters, may be {@code null} to clear
         * @return this builder
         */
        public Builder properties(Map<String, Object> properties) {
            this.properties = properties == null ? new LinkedHashMap<>() : new LinkedHashMap<>(properties);
            return this;
        }

        /**
         * Adds one provider parameter.
         *
         * @param key   parameter key
         * @param value parameter value
         * @return this builder
         */
        public Builder property(String key, Object value) {
            this.properties.put(key, value);
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return container configuration, never {@code null}
         */
        public ContainerConfiguration build() {
            return new ContainerConfiguration(this);
        }
    }
}
