package io.github.cocosip.polystore;

/**
 * Tenant isolation mode of a container. Polystore does not manage tenants; isolation means one
 * specific thing: files of different tenants are physically separated inside the same container
 * (bucket) by path prefix.
 */
public enum TenantIsolationMode {

    /** All tenants share the same path space (default). */
    NONE,

    /** Every file path is transparently prefixed with {@code {tenantId}/}. */
    PATH_PREFIX
}
