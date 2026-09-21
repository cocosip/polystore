package io.github.cocosip.polystore;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContainerConfigurationTest {

    @Test
    void builderShouldDefaultToNonDefaultContainerWithoutIsolation() {
        ContainerConfiguration config =
                ContainerConfiguration.builder().name("images").type("local").build();

        assertThat(config.getName()).isEqualTo("images");
        assertThat(config.getType()).isEqualTo("local");
        assertThat(config.isDefault()).isFalse();
        assertThat(config.getTenantIsolation()).isEqualTo(TenantIsolationMode.NONE);
        assertThat(config.getProperties()).isEmpty();
    }

    @Test
    void builderShouldSetAllFields() {
        ContainerConfiguration config = ContainerConfiguration.builder()
                .name("dicom")
                .type("minio")
                .isDefault(true)
                .tenantIsolation(TenantIsolationMode.PATH_PREFIX)
                .property("endpoint", "http://minio.internal:9000")
                .property("bucketName", "dicom")
                .build();

        assertThat(config.isDefault()).isTrue();
        assertThat(config.getTenantIsolation()).isEqualTo(TenantIsolationMode.PATH_PREFIX);
        assertThat(config.getProperty("endpoint")).isEqualTo("http://minio.internal:9000");
        assertThat(config.getProperty("bucketName")).isEqualTo("dicom");
        assertThat(config.getProperty("missing")).isNull();
    }

    @Test
    void propertiesShouldBeDefensivelyCopied() {
        Map<String, Object> source = new HashMap<>();
        source.put("endpoint", "a");

        ContainerConfiguration config =
                ContainerConfiguration.builder().properties(source).build();
        source.put("endpoint", "b");

        assertThat(config.getProperty("endpoint")).isEqualTo("a");
    }

    @Test
    void propertiesShouldBeUnmodifiable() {
        ContainerConfiguration config =
                ContainerConfiguration.builder().property("k", "v").build();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> config.getProperties().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullPropertiesShouldBeTreatedAsEmpty() {
        ContainerConfiguration config =
                ContainerConfiguration.builder().properties(null).build();

        assertThat(config.getProperties()).isEmpty();
    }

    @Test
    void toStringShouldContainKeyFields() {
        ContainerConfiguration config =
                ContainerConfiguration.builder().name("c1").type("s3").build();

        assertThat(config.toString()).contains("c1").contains("s3");
    }
}
