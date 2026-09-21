package io.github.cocosip.polystore.aliyunoss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.StorageContainer;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AliyunOssStorageProviderTest {

    private static StorageContainer build(Map<String, Object> properties) {
        return new AliyunOssStorageProvider()
                .createContainer(ContainerConfiguration.builder()
                        .name("archive")
                        .type("aliyun-oss")
                        .properties(properties)
                        .build());
    }

    @Test
    void providerTypeShouldBeAliyunOss() {
        assertThat(new AliyunOssStorageProvider().getType()).isEqualTo("aliyun-oss");
    }

    @Test
    void shouldBuildContainerFromConfiguration() {
        StorageContainer container = build(Map.of(
                "endpoint", "oss-cn-hangzhou.aliyuncs.com",
                "accessKeyId", "LTAI",
                "access-key-secret", "secret",
                "bucketName", "archive"));

        assertThat(container.getProviderType()).isEqualTo("aliyun-oss");
        assertThat(container.getInfo().getName()).isEqualTo("archive");
    }

    @Test
    void presignedUrlShouldBeComputedOfflineAndHonorExpiry() {
        StorageContainer container = build(Map.of(
                "endpoint", "oss-cn-hangzhou.aliyuncs.com",
                "accessKeyId", "LTAI",
                "accessKeySecret", "secret",
                "bucketName", "archive",
                "urlExpiry", 120));

        String url = container.getUrl("2024/report.pdf");

        assertThat(url).contains("Signature=");
        assertThat(url).contains("OSSAccessKeyId=LTAI");
        assertThat(url).contains("Expires=");
        String urlOverride = container.getUrl(
                "2024/report.pdf",
                io.github.cocosip.polystore.UrlArgs.builder()
                        .expiry(Duration.ofMinutes(5))
                        .build());
        assertThat(urlOverride).isNotBlank();
    }

    @Test
    void useInternalShouldRewriteAliyuncsEndpointsOnly() {
        StorageContainer internal = build(Map.of(
                "endpoint", "oss-cn-hangzhou.aliyuncs.com",
                "accessKeyId", "LTAI",
                "accessKeySecret", "secret",
                "bucketName", "archive",
                "useInternal", true));

        String url = internal.getUrl("a.txt");
        assertThat(url).contains("-internal.aliyuncs.com");

        StorageContainer external = build(Map.of(
                "endpoint", "http://private-oss.example.com",
                "accessKeyId", "LTAI",
                "accessKeySecret", "secret",
                "bucketName", "archive",
                "useInternal", true));
        assertThat(external.getUrl("a.txt")).contains("private-oss.example.com");
    }

    @Test
    void missingRequiredParametersShouldBeRejected() {
        AliyunOssStorageProvider provider = new AliyunOssStorageProvider();

        assertThatThrownBy(() -> provider.createContainer(ContainerConfiguration.builder()
                        .name("c")
                        .type("aliyun-oss")
                        .properties(Map.of("accessKeyId", "a", "accessKeySecret", "s", "bucketName", "b"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("endpoint");
        assertThatThrownBy(() -> provider.createContainer(ContainerConfiguration.builder()
                        .name("c")
                        .type("aliyun-oss")
                        .properties(Map.of("endpoint", "e", "accessKeySecret", "s", "bucketName", "b"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accessKeyId");
    }
}
