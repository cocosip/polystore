package io.github.cocosip.polystore.spring;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.DefaultStorageContainer;
import io.github.cocosip.polystore.StorageBackend;
import io.github.cocosip.polystore.StorageContainer;
import io.github.cocosip.polystore.StorageManager;
import io.github.cocosip.polystore.StorageProvider;
import io.github.cocosip.polystore.TenantIdSupplier;
import io.github.cocosip.polystore.TenantIsolationMode;
import io.github.cocosip.polystore.exception.ContainerNotFoundException;
import io.github.cocosip.polystore.exception.StorageProviderNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Default {@link StorageManager}: creates every configured container at startup and wraps it with
 * the enabled integrations, from the inside out:
 *
 * <ol>
 *   <li>the backend container created by the {@link StorageProvider}</li>
 *   <li>{@link TenantPathPrefixContainer} when the container uses
 *       {@code TenantIsolationMode.PATH_PREFIX}</li>
 *   <li>{@link EventPublishingContainer} when an event publisher is present</li>
 * </ol>
 *
 * <p>Providers are matched to configurations case-insensitively, because SharpAbp provider names
 * are PascalCase ({@code Minio}, {@code S3}, {@code Aws}, {@code Azure}) while a provider
 * implementation may register a lowercase type.</p>
 */
public final class DefaultStorageManager implements StorageManager {

    private final Map<String, StorageContainer> containers;
    private final String defaultContainerName;

    /**
     * Creates the manager and initializes every container.
     *
     * @param configurations   container definitions, never {@code null}
     * @param providers        registered providers, looked up by
     *                         {@link StorageProvider#getType()} ignoring case, never {@code null}
     * @param tenantIdSupplier tenant id source for PATH_PREFIX containers, may be {@code null}
     * @param eventPublisher   event sink, may be {@code null} to disable event publishing
     * @throws IllegalStateException             on duplicate container names or more than one
     *                                           container marked as default
     * @throws StorageProviderNotFoundException  if a configuration references an unregistered
     *                                           provider type
     */
    public DefaultStorageManager(
            List<ContainerConfiguration> configurations,
            Collection<StorageProvider> providers,
            TenantIdSupplier tenantIdSupplier,
            StorageEventPublisher eventPublisher) {
        Map<String, StorageProvider> providersByType = new LinkedHashMap<>();
        for (StorageProvider provider : providers) {
            providersByType.put(normalizedType(provider.getType()), provider);
        }
        // aliases never shadow a canonical type, whatever the registration order is
        for (StorageProvider provider : providers) {
            for (String alias : provider.getAliases()) {
                providersByType.putIfAbsent(normalizedType(alias), provider);
            }
        }

        Map<String, StorageContainer> created = new LinkedHashMap<>();
        String defaultName = null;
        int defaultCount = 0;
        for (ContainerConfiguration configuration : configurations) {
            if (created.containsKey(configuration.getName())) {
                throw new IllegalStateException("Duplicate container name: " + configuration.getName());
            }
            StorageProvider provider = providersByType.get(normalizedType(configuration.getType()));
            if (provider == null) {
                throw new StorageProviderNotFoundException(configuration.getType());
            }
            StorageContainer container =
                    DefaultStorageContainer.from(configuration, createBackend(provider, configuration));
            if (container.getInfo().isDefault()) {
                defaultName = configuration.getName();
                defaultCount++;
            }
            created.put(configuration.getName(), wrap(configuration, container, tenantIdSupplier, eventPublisher));
        }
        if (defaultCount > 1) {
            throw new IllegalStateException("More than one container is marked as default: " + defaultCount);
        }
        this.containers = Collections.unmodifiableMap(created);
        this.defaultContainerName = defaultName;
    }

    private static String normalizedType(String type) {
        return type == null ? "" : type.toLowerCase(Locale.ROOT);
    }

    /**
     * Creates the backend of one container, naming the container when the provider rejects the
     * configuration — the raw validation errors (e.g. "Missing required storage parameter
     * 'endPoint'") do not carry it themselves.
     */
    private static StorageBackend createBackend(StorageProvider provider, ContainerConfiguration configuration) {
        try {
            return provider.createBackend(configuration);
        } catch (IllegalStateException e) {
            throw new IllegalStateException(
                    "Invalid configuration of container '" + configuration.getName() + "': " + e.getMessage(), e);
        }
    }

    private static StorageContainer wrap(
            ContainerConfiguration configuration,
            StorageContainer container,
            TenantIdSupplier tenantIdSupplier,
            StorageEventPublisher eventPublisher) {
        StorageContainer wrapped = container;
        if (configuration.getTenantIsolation() == TenantIsolationMode.PATH_PREFIX) {
            wrapped = new TenantPathPrefixContainer(wrapped, tenantIdSupplier);
        }
        if (eventPublisher != null) {
            wrapped = new EventPublishingContainer(wrapped, eventPublisher);
        }
        return wrapped;
    }

    @Override
    public StorageContainer getContainer(String name) {
        StorageContainer container = containers.get(name);
        if (container == null) {
            throw new ContainerNotFoundException(name);
        }
        return container;
    }

    @Override
    public StorageContainer getDefaultContainer() {
        if (defaultContainerName == null) {
            throw new ContainerNotFoundException("default");
        }
        return getContainer(defaultContainerName);
    }

    @Override
    public Collection<String> containerNames() {
        return Collections.unmodifiableList(new ArrayList<>(containers.keySet()));
    }
}
