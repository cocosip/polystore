package io.github.cocosip.polystore.exception;

/**
 * Thrown when a container with {@link io.github.cocosip.polystore.TenantIsolationMode#PATH_PREFIX}
 * cannot resolve a tenant id: neither
 * {@link io.github.cocosip.polystore.StorageSaveOptions#getTenantId()} nor the
 * {@link io.github.cocosip.polystore.TenantIdSupplier} provided one.
 */
public class TenantIdMissingException extends PolystoreException {

    private static final long serialVersionUID = 1L;

    private final String containerName;

    /** Creates the exception for the given container. */
    public TenantIdMissingException(String containerName) {
        super("Tenant id is required by container '" + containerName
                + "' with PATH_PREFIX isolation, but neither StorageSaveOptions.tenantId nor the"
                + " TenantIdSupplier provided one");
        this.containerName = containerName;
    }

    /**
     * Returns the name of the container that required the tenant id.
     *
     * @return container name
     */
    public String getContainerName() {
        return containerName;
    }
}
