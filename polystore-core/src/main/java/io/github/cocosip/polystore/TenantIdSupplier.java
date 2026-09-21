package io.github.cocosip.polystore;

/**
 * Supplies the current tenant id. Register one application-wide (e.g. reading from your tenant
 * context); call sites then stay free of tenant boilerplate.
 *
 * <p>Resolution priority: {@link SaveArgs#getTenantId()} (explicit) &gt; {@code TenantIdSupplier}
 * (implicit) &gt; {@code null}. A final {@code null} is fine for {@link TenantIsolationMode#NONE},
 * but raises {@code TenantIdMissingException} under {@link TenantIsolationMode#PATH_PREFIX}.</p>
 */
@FunctionalInterface
public interface TenantIdSupplier {

    /**
     * Returns the current tenant id, or {@code null} when the application is not multi-tenant.
     *
     * @return current tenant id, may be {@code null}
     */
    String get();
}
