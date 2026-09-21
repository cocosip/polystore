package io.github.cocosip.polystore.spring;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.cocosip.polystore.TenantIsolationMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code polystore.*} configuration namespace.
 *
 * <p>Typed fields cover the container identity; provider-specific sections (e.g. a {@code minio}
 * block under a container) are intentionally not declared here because their keys are backend
 * defined. {@link ContainerConfigurationFactory} reads those sections directly from the
 * environment and merges them into each {@code ContainerConfiguration}.</p>
 *
 * <pre>
 * polystore:
 *   containers:
 *     - name: dicom
 *       type: minio
 *       tenant-isolation: PATH_PREFIX
 *       minio:
 *         end-point: minio.internal:9000
 * </pre>
 */
@ConfigurationProperties(prefix = "polystore")
public class PolystoreProperties {

    private List<ContainerProperties> containers = new ArrayList<>();

    /**
     * Returns the configured containers, in declaration order.
     *
     * @return container definitions, never {@code null}
     */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "configuration properties must expose the mutable list for binding")
    public List<ContainerProperties> getContainers() {
        return containers;
    }

    /**
     * Replaces the container definitions.
     *
     * @param containers container definitions
     */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "configuration properties must accept the binder's list instance")
    public void setContainers(List<ContainerProperties> containers) {
        this.containers = containers;
    }

    /** Typed definition of one container entry. */
    public static class ContainerProperties {

        private String name;
        private String type;
        private Boolean isDefault;
        private TenantIsolationMode tenantIsolation;
        private Boolean enableAutoMultiPartUpload;
        private Long multiPartUploadMinFileSize;
        private Long multiPartUploadShardingSize;
        private Boolean httpAccess;

        /**
         * Returns the unique container name.
         *
         * @return container name
         */
        public String getName() {
            return name;
        }

        /**
         * Sets the unique container name.
         *
         * @param name container name
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * Returns the provider type identifier, e.g. {@code local}, {@code minio}.
         *
         * @return provider type identifier
         */
        public String getType() {
            return type;
        }

        /**
         * Sets the provider type identifier.
         *
         * @param type provider type identifier
         */
        public void setType(String type) {
            this.type = type;
        }

        /**
         * Returns whether this container is the default one, or {@code null} when unset. Bound
         * from the {@code default} key.
         *
         * @return default flag, may be {@code null}
         */
        public Boolean getDefault() {
            return isDefault;
        }

        /**
         * Sets whether this container is the default one. Bound from the {@code default} key.
         *
         * @param isDefault default flag
         */
        public void setDefault(Boolean isDefault) {
            this.isDefault = isDefault;
        }

        /**
         * Returns the tenant isolation mode, or {@code null} when unset.
         *
         * @return isolation mode, may be {@code null}
         */
        public TenantIsolationMode getTenantIsolation() {
            return tenantIsolation;
        }

        /**
         * Sets the tenant isolation mode.
         *
         * @param tenantIsolation isolation mode
         */
        public void setTenantIsolation(TenantIsolationMode tenantIsolation) {
            this.tenantIsolation = tenantIsolation;
        }

        /** Returns whether automatic multipart upload is enabled. */
        public Boolean getEnableAutoMultiPartUpload() {
            return enableAutoMultiPartUpload;
        }

        /** Sets whether automatic multipart upload is enabled. */
        public void setEnableAutoMultiPartUpload(Boolean value) {
            enableAutoMultiPartUpload = value;
        }

        /** Returns the multipart selection threshold in bytes. */
        public Long getMultiPartUploadMinFileSize() {
            return multiPartUploadMinFileSize;
        }

        /** Sets the multipart selection threshold in bytes. */
        public void setMultiPartUploadMinFileSize(Long value) {
            multiPartUploadMinFileSize = value;
        }

        /** Returns the multipart part size in bytes. */
        public Long getMultiPartUploadShardingSize() {
            return multiPartUploadShardingSize;
        }

        /** Sets the multipart part size in bytes. */
        public void setMultiPartUploadShardingSize(Long value) {
            multiPartUploadShardingSize = value;
        }

        /** Returns whether public access URLs are enabled. */
        public Boolean getHttpAccess() {
            return httpAccess;
        }

        /** Sets whether public access URLs are enabled. */
        public void setHttpAccess(Boolean value) {
            httpAccess = value;
        }
    }
}
