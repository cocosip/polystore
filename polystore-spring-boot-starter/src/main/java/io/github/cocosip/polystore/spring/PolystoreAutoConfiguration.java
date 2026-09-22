package io.github.cocosip.polystore.spring;

import io.github.cocosip.polystore.StorageManager;
import io.github.cocosip.polystore.StorageProvider;
import io.github.cocosip.polystore.TenantIdSupplier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Auto-configuration: binds {@code polystore.*} properties, discovers {@link StorageProvider}s
 * and exposes a {@link StorageManager} bean.
 *
 * <p>Providers are discovered in two ways, with Spring beans taking precedence on a type clash:
 * beans of type {@link StorageProvider} in the application context, and implementations declared
 * through {@link ServiceLoader} ({@code META-INF/services/io.github.cocosip.polystore.StorageProvider})
 * shipped by the backend modules.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(PolystoreProperties.class)
public class PolystoreAutoConfiguration {

    /** Creates the auto-configuration. */
    public PolystoreAutoConfiguration() {}

    /**
     * Builds the storage manager from the configured containers and the discovered providers.
     *
     * @param environment       environment for reading provider-specific sections
     * @param properties        typed container definitions
     * @param providerBeans     {@link StorageProvider} beans; override ServiceLoader entries
     * @param tenantIdSupplier  optional tenant id source for PATH_PREFIX containers
     * @param eventPublisher    optional Spring event publisher, enables storage events
     * @return the storage manager, never {@code null}
     */
    @Bean
    @ConditionalOnMissingBean(StorageManager.class)
    public DefaultStorageManager storageManager(
            Environment environment,
            PolystoreProperties properties,
            ObjectProvider<StorageProvider> providerBeans,
            ObjectProvider<TenantIdSupplier> tenantIdSupplier,
            ObjectProvider<ApplicationEventPublisher> eventPublisher) {
        Map<String, StorageProvider> providers = new LinkedHashMap<>();
        for (StorageProvider provider : ServiceLoader.load(StorageProvider.class, classLoader())) {
            providers.put(provider.getType(), provider);
        }
        providerBeans.orderedStream().forEach(provider -> providers.put(provider.getType(), provider));

        ApplicationEventPublisher publisher = eventPublisher.getIfAvailable();
        return new DefaultStorageManager(
                ContainerConfigurationFactory.create(properties, environment),
                new ArrayList<>(providers.values()),
                tenantIdSupplier.getIfAvailable(),
                publisher == null ? null : publisher::publishEvent);
    }

    private static ClassLoader classLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader != null ? classLoader : PolystoreAutoConfiguration.class.getClassLoader();
    }
}
