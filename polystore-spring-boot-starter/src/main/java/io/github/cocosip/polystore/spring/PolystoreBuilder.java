package io.github.cocosip.polystore.spring;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.StorageProvider;
import io.github.cocosip.polystore.TenantIdSupplier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manual assembly point for non-Spring environments (or tests): register providers and container
 * configurations, then {@link #build()} a ready {@link DefaultStorageManager}.
 *
 * <pre>
 * StorageManager manager = PolystoreBuilder.builder()
 *     .addProvider(new LocalStorageProvider())
 *     .addConfiguration(ContainerConfiguration.builder()
 *         .name("images")
 *         .type("local")
 *         .property("basePath", "/data/images")
 *         .build())
 *     .tenantIdSupplier(() -&#62; MyTenantContext.currentTenantId())
 *     .build();
 * </pre>
 */
public final class PolystoreBuilder {

    private final List<ContainerConfiguration> configurations = new ArrayList<>();
    private final Map<String, StorageProvider> providers = new LinkedHashMap<>();
    private TenantIdSupplier tenantIdSupplier;
    private StorageEventPublisher eventPublisher;

    private PolystoreBuilder() {}

    /**
     * Returns a new builder.
     *
     * @return builder, never {@code null}
     */
    public static PolystoreBuilder builder() {
        return new PolystoreBuilder();
    }

    /**
     * Replaces the container configurations.
     *
     * @param configurations container definitions
     * @return this builder
     */
    public PolystoreBuilder configurations(Collection<ContainerConfiguration> configurations) {
        this.configurations.clear();
        this.configurations.addAll(configurations);
        return this;
    }

    /**
     * Adds one container configuration.
     *
     * @param configuration container definition
     * @return this builder
     */
    public PolystoreBuilder addConfiguration(ContainerConfiguration configuration) {
        this.configurations.add(configuration);
        return this;
    }

    /**
     * Replaces the registered providers.
     *
     * @param providers storage backends
     * @return this builder
     */
    public PolystoreBuilder providers(StorageProvider... providers) {
        return providers(Arrays.asList(providers));
    }

    /**
     * Replaces the registered providers.
     *
     * @param providers storage backends
     * @return this builder
     */
    public PolystoreBuilder providers(Collection<StorageProvider> providers) {
        this.providers.clear();
        providers.forEach(this::addProvider);
        return this;
    }

    /**
     * Registers one provider, replacing a previously registered provider of the same type.
     *
     * @param provider storage backend
     * @return this builder
     */
    public PolystoreBuilder addProvider(StorageProvider provider) {
        this.providers.put(provider.getType(), provider);
        return this;
    }

    /**
     * Sets the tenant id source for PATH_PREFIX containers.
     *
     * @param tenantIdSupplier tenant id source, may be {@code null}
     * @return this builder
     */
    public PolystoreBuilder tenantIdSupplier(TenantIdSupplier tenantIdSupplier) {
        this.tenantIdSupplier = tenantIdSupplier;
        return this;
    }

    /**
     * Sets the event sink for storage operation events.
     *
     * @param eventPublisher event sink, may be {@code null} to disable events
     * @return this builder
     */
    public PolystoreBuilder eventPublisher(StorageEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        return this;
    }

    /**
     * Builds the manager and initializes every container.
     *
     * @return storage manager, never {@code null}
     */
    public DefaultStorageManager build() {
        return new DefaultStorageManager(configurations, providers.values(), tenantIdSupplier, eventPublisher);
    }
}
