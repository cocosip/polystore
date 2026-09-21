package io.github.cocosip.polystore.huaweiobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.StorageContainer;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HuaweiObsStorageProviderTest {

    private static final Map<String, Object> CONFIG = Map.of(
            "endpoint", "obs.cn-north-4.myhuaweicloud.com",
            "accessKey", "ak",
            "secretKey", "sk",
            "bucketName", "dicom");

    private static StorageContainer build(Map<String, Object> properties) {
        return new HuaweiObsStorageProvider()
                .createContainer(ContainerConfiguration.builder()
                        .name("obs")
                        .type("huawei-obs")
                        .properties(properties)
                        .build());
    }

    @Test
    void providerTypeShouldBeHuaweiObs() {
        assertThat(new HuaweiObsStorageProvider().getType()).isEqualTo("huawei-obs");
    }

    @Test
    void shouldBuildContainerFromConfiguration() {
        StorageContainer container = build(CONFIG);

        assertThat(container.getProviderType()).isEqualTo("huawei-obs");
        assertThat(container.getInfo().getName()).isEqualTo("obs");
    }

    @Test
    void signedUrlShouldBeComputedOffline() {
        StorageContainer container = build(CONFIG);

        String url = container.getUrl("2024/scan.dcm");

        assertThat(url).contains("obs.cn-north-4.myhuaweicloud.com");
        assertThat(url).contains("Signature=");
        assertThat(url).contains("Expires=");
    }

    @Test
    void explicitUrlArgsExpiryShouldBeAccepted() {
        StorageContainer container = build(CONFIG);

        String url = container.getUrl(
                "a.txt",
                io.github.cocosip.polystore.UrlArgs.builder()
                        .expiry(Duration.ofMinutes(10))
                        .build());

        assertThat(url).isNotBlank();
    }

    @Test
    void missingRequiredParametersShouldBeRejected() {
        HuaweiObsStorageProvider provider = new HuaweiObsStorageProvider();

        assertThatThrownBy(() -> provider.createContainer(ContainerConfiguration.builder()
                        .name("c")
                        .type("huawei-obs")
                        .properties(Map.of("accessKey", "a", "secretKey", "s", "bucketName", "b"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("endpoint");
        assertThatThrownBy(() -> provider.createContainer(ContainerConfiguration.builder()
                        .name("c")
                        .type("huawei-obs")
                        .properties(Map.of("endpoint", "e", "accessKey", "a", "secretKey", "s"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bucketName");
    }
}
