package io.github.cocosip.polystore.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cocosip.polystore.TenantIsolationMode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ContainerConfigurationFactoryTest {

    private static PolystoreProperties properties(PolystoreProperties.ContainerProperties... containers) {
        PolystoreProperties properties = new PolystoreProperties();
        properties.setContainers(List.of(containers));
        return properties;
    }

    private static PolystoreProperties.ContainerProperties container(String name, String type) {
        PolystoreProperties.ContainerProperties container = new PolystoreProperties.ContainerProperties();
        container.setName(name);
        container.setType(type);
        return container;
    }

    @Test
    void shouldMergeTypedFieldsAndProviderSection() {
        PolystoreProperties.ContainerProperties dicom = container("dicom", "minio");
        dicom.setDefault(true);
        dicom.setTenantIsolation(TenantIsolationMode.PATH_PREFIX);
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("polystore.containers[0].minio.endpoint", "http://minio.internal:9000");
        environment.setProperty("polystore.containers[0].minio.access-key", "admin");

        List<io.github.cocosip.polystore.ContainerConfiguration> configurations =
                ContainerConfigurationFactory.create(properties(dicom), environment);

        assertThat(configurations).hasSize(1);
        io.github.cocosip.polystore.ContainerConfiguration config = configurations.get(0);
        assertThat(config.getName()).isEqualTo("dicom");
        assertThat(config.getType()).isEqualTo("minio");
        assertThat(config.isDefault()).isTrue();
        assertThat(config.getTenantIsolation()).isEqualTo(TenantIsolationMode.PATH_PREFIX);
        assertThat(config.getProperty("endpoint")).isEqualTo("http://minio.internal:9000");
        assertThat(config.getProperty("access-key")).isEqualTo("admin");
    }

    @Test
    void missingProviderSectionShouldYieldEmptyProperties() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("polystore.containers[0].name", "images");
        environment.setProperty("polystore.containers[0].type", "local");

        List<io.github.cocosip.polystore.ContainerConfiguration> configurations =
                ContainerConfigurationFactory.create(properties(container("images", "local")), environment);

        assertThat(configurations.get(0).getProperties()).isEmpty();
        assertThat(configurations.get(0).getTenantIsolation()).isEqualTo(TenantIsolationMode.NONE);
    }

    @Test
    void sectionKeysOtherThanTypeShouldBeIgnored() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("polystore.containers[0].minio.endpoint", "http://x");

        List<io.github.cocosip.polystore.ContainerConfiguration> configurations =
                ContainerConfigurationFactory.create(properties(container("backup", "s3")), environment);

        // type=s3 but only a minio section exists → no properties leak across providers
        assertThat(configurations.get(0).getProperties()).isEmpty();
    }

    @Test
    void providerSectionShouldResolveIgnoringTypeCase() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("polystore.containers[0].minio.endpoint", "http://minio.internal:9000");

        io.github.cocosip.polystore.ContainerConfiguration config = ContainerConfigurationFactory.create(
                        properties(container("dicom", "Minio")), environment)
                .get(0);

        assertThat(config.getType()).isEqualTo("Minio");
        assertThat(config.getProperty("endpoint")).isEqualTo("http://minio.internal:9000");
    }

    @Test
    void providerSectionShouldResolveIgnoringTypeSeparators() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("polystore.containers[0].aliyun_oss.endpoint", "http://oss.internal");

        io.github.cocosip.polystore.ContainerConfiguration config = ContainerConfigurationFactory.create(
                        properties(container("dicom", "Aliyun-Oss")), environment)
                .get(0);

        assertThat(config.getType()).isEqualTo("Aliyun-Oss");
        assertThat(config.getProperty("endpoint")).isEqualTo("http://oss.internal");
    }

    @Test
    void scalarProviderSectionOfAnotherSpellingShouldBeRejected() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("polystore.containers[0].minio", "oops");

        assertThatThrownBy(() ->
                        ContainerConfigurationFactory.create(properties(container("dicom", "Minio")), environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mapping");
    }

    @Test
    void scalarProviderSectionShouldBeRejected() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("polystore.containers[0].minio", "oops");

        assertThatThrownBy(() ->
                        ContainerConfigurationFactory.create(properties(container("dicom", "minio")), environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mapping");
    }

    @Test
    void blankNameOrTypeShouldBeRejected() {
        MockEnvironment environment = new MockEnvironment();

        assertThatThrownBy(
                        () -> ContainerConfigurationFactory.create(properties(container(null, "local")), environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("name");
        assertThatThrownBy(() -> ContainerConfigurationFactory.create(properties(container("images", "")), environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("type");
    }

    @Test
    void nestedSectionValuesShouldBePreservedAsObjects() {
        PolystoreProperties.ContainerProperties entry = container("dicom", "minio");
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("polystore.containers[0].minio.endpoint", "http://x");

        Map<String, Object> props = ContainerConfigurationFactory.create(properties(entry), environment)
                .get(0)
                .getProperties();

        assertThat(props).containsKey("endpoint");
    }
}
