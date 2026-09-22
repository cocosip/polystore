package io.github.cocosip.polystore.spring;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.TenantIsolationMode;
import io.github.cocosip.polystore.util.ConfigUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Assembles {@link ContainerConfiguration} instances from {@link PolystoreProperties}.
 *
 * <p>The typed properties cover identity fields (name, type, default flag, tenant isolation);
 * the provider-specific section is read from the same {@code polystore.containers[n]} entries via
 * the {@code Binder} API and keyed by the provider type string — a container of {@code type:
 * minio} reads its parameters from the sibling {@code minio:} block. The section key is resolved
 * first exactly and then by normalized comparison (case-, {@code -} and {@code _}-insensitive), so
 * {@code type: Minio} reads a {@code minio:} block and {@code type: Aliyun-Oss} reads an
 * {@code aliyun_oss:} block. Section keys are copied as written (kebab-case keys stay kebab-case);
 * backends normalize when reading them.</p>
 */
public final class ContainerConfigurationFactory {

    private ContainerConfigurationFactory() {}

    /**
     * Creates one configuration per declared container, in declaration order.
     *
     * @param properties  typed container definitions, never {@code null}
     * @param environment environment carrying the raw {@code polystore.containers} entries
     * @return container configurations, never {@code null}
     * @throws IllegalStateException if a container lacks name/type or its provider section is not
     *                               a mapping
     */
    public static List<ContainerConfiguration> create(PolystoreProperties properties, Environment environment) {
        List<PolystoreProperties.ContainerProperties> typed = properties.getContainers();
        List<Map<String, Object>> raw = bindRawContainers(environment);

        List<ContainerConfiguration> configurations = new ArrayList<>(typed.size());
        for (int i = 0; i < typed.size(); i++) {
            PolystoreProperties.ContainerProperties container = typed.get(i);
            requireNameAndType(container);
            Map<String, Object> section =
                    i < raw.size() ? providerSection(raw.get(i), container.getName(), container.getType()) : Map.of();
            configurations.add(toConfiguration(container, section));
        }
        return configurations;
    }

    private static void requireNameAndType(PolystoreProperties.ContainerProperties container) {
        if (isBlank(container.getName())) {
            throw new IllegalStateException("polystore.containers[].name must not be blank");
        }
        if (isBlank(container.getType())) {
            throw new IllegalStateException("polystore.containers[" + container.getName() + "].type must not be blank");
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<Map<String, Object>> bindRawContainers(Environment environment) {
        // The Binder API returns the entries as written, including provider sections that the
        // typed PolystoreProperties ignores.
        List<Map> entries = Binder.get(environment)
                .bind("polystore.containers", Bindable.listOf(Map.class))
                .orElse(List.of());
        return (List<Map<String, Object>>) (List<?>) entries;
    }

    private static Map<String, Object> providerSection(Map<String, Object> entry, String containerName, String type) {
        Object section = entry.get(type);
        if (section == null) {
            section = findSectionByNormalizedKey(entry, type);
        }
        if (section == null) {
            // a provider without required parameters (e.g. a custom one) may come up with an
            // empty property map; backends reject genuinely missing parameters by name
            return Map.of();
        }
        if (!(section instanceof Map)) {
            throw new IllegalStateException("Provider section '" + type + "' of container '" + containerName
                    + "' must be a mapping, but was: " + section.getClass().getSimpleName());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) section;
        return new LinkedHashMap<>(properties);
    }

    private static Object findSectionByNormalizedKey(Map<String, Object> entry, String type) {
        String normalizedType = ConfigUtils.normalizeKey(type);
        if (normalizedType == null) {
            return null;
        }
        for (Map.Entry<String, Object> candidate : entry.entrySet()) {
            if (normalizedType.equals(ConfigUtils.normalizeKey(candidate.getKey()))) {
                return candidate.getValue();
            }
        }
        return null;
    }

    private static ContainerConfiguration toConfiguration(
            PolystoreProperties.ContainerProperties container, Map<String, Object> section) {
        ContainerConfiguration.Builder builder = ContainerConfiguration.builder()
                .name(container.getName())
                .type(container.getType())
                .isDefault(Boolean.TRUE.equals(container.getDefault()))
                .tenantIsolation(
                        container.getTenantIsolation() == null
                                ? TenantIsolationMode.NONE
                                : container.getTenantIsolation())
                .enableAutoMultiPartUpload(Boolean.TRUE.equals(container.getEnableAutoMultiPartUpload()))
                .httpAccess(container.getHttpAccess() == null || container.getHttpAccess())
                .properties(section);
        if (container.getMultiPartUploadMinFileSize() != null) {
            builder.multiPartUploadMinFileSize(container.getMultiPartUploadMinFileSize());
        }
        if (container.getMultiPartUploadShardingSize() != null) {
            builder.multiPartUploadShardingSize(container.getMultiPartUploadShardingSize());
        }
        return builder.build();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
