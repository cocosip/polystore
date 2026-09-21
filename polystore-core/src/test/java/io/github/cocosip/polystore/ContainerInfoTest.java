package io.github.cocosip.polystore;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContainerInfoTest {

    @Test
    void shouldExposeConstructorFields() {
        ContainerInfo info = new ContainerInfo("images", "local", true);

        assertThat(info.getName()).isEqualTo("images");
        assertThat(info.getProviderType()).isEqualTo("local");
        assertThat(info.isDefault()).isTrue();
    }

    @Test
    void fromShouldMapConfigurationFields() {
        ContainerConfiguration config = ContainerConfiguration.builder()
                .name("dicom")
                .type("minio")
                .isDefault(true)
                .property("endpoint", "http://minio.internal:9000")
                .build();

        ContainerInfo info = ContainerInfo.from(config);

        assertThat(info.getName()).isEqualTo("dicom");
        assertThat(info.getProviderType()).isEqualTo("minio");
        assertThat(info.isDefault()).isTrue();
    }

    @Test
    void toStringShouldContainKeyFields() {
        assertThat(new ContainerInfo("c", "local", false).toString())
                .contains("c")
                .contains("local");
    }
}
