package io.github.cocosip.polystore.minio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.StorageContainer;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MinioStorageProviderTest {

    private static ContainerConfiguration config(Map<String, Object> properties) {
        ContainerConfiguration.Builder builder =
                ContainerConfiguration.builder().name("dicom").type("minio").properties(properties);
        return builder.build();
    }

    private static final Map<String, Object> FULL = Map.of(
            "endpoint", "http://minio.internal:9000",
            "access-key", "admin",
            "secretKey", "secret",
            "bucketName", "dicom",
            "region", "us-east-1");

    @Test
    void providerTypeShouldBeMinio() {
        assertThat(new MinioStorageProvider().getType()).isEqualTo("minio");
    }

    @Test
    void shouldBuildContainerFromConfiguration() {
        StorageContainer container = new MinioStorageProvider().createContainer(config(FULL));

        assertThat(container.getName()).isEqualTo("dicom");
        assertThat(container.getProviderType()).isEqualTo("minio");
        assertThat(container.getInfo().getProviderType()).isEqualTo("minio");
    }

    @Test
    void presignedUrlShouldBeComputedOffline() {
        StorageContainer container = new MinioStorageProvider().createContainer(config(FULL));

        String url = container.getUrl("2024/scan.dcm");

        assertThat(url).startsWith("http://minio.internal:9000/dicom/2024/scan.dcm");
        assertThat(url).contains("X-Amz-Signature=");
        assertThat(url).contains("X-Amz-Expires=3600");
    }

    @Test
    void urlExpiryParameterShouldControlPresignExpiry() {
        StorageContainer container = new MinioStorageProvider()
                .createContainer(config(Map.of(
                        "endpoint",
                        "http://minio.internal:9000",
                        "accessKey",
                        "admin",
                        "secret-key",
                        "secret",
                        "bucket-name",
                        "dicom",
                        "region",
                        "us-east-1",
                        "urlExpiry",
                        60)));

        assertThat(container.getUrl("a.txt")).contains("X-Amz-Expires=60");
    }

    @Test
    void secureShouldForceHttpsWhenEndpointHasNoScheme() {
        StorageContainer container = new MinioStorageProvider()
                .createContainer(config(Map.of(
                        "endpoint",
                        "minio.internal:9000",
                        "secure",
                        true,
                        "accessKey",
                        "admin",
                        "secretKey",
                        "secret",
                        "bucketName",
                        "dicom",
                        "region",
                        "us-east-1")));

        assertThat(container.getUrl("a.txt")).startsWith("https://minio.internal:9000/");
    }

    @Test
    void missingRequiredParametersShouldBeRejected() {
        MinioStorageProvider provider = new MinioStorageProvider();

        assertThatThrownBy(() ->
                        provider.createContainer(config(Map.of("accessKey", "a", "secretKey", "s", "bucketName", "b"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("endpoint");
        assertThatThrownBy(() -> provider.createContainer(
                        config(Map.of("endpoint", "http://x", "secretKey", "s", "bucketName", "b"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accessKey");
        assertThatThrownBy(() -> provider.createContainer(
                        config(Map.of("endpoint", "http://x", "accessKey", "a", "secretKey", "s"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bucketName");
    }

    @Test
    void createBucketIfAbsentShouldBeOptIn() {
        // default: no network call at container creation; container builds fine for an
        // unreachable endpoint
        StorageContainer container = new MinioStorageProvider()
                .createContainer(config(Map.of(
                        "endpoint", "http://127.0.0.1:1", "accessKey", "a", "secretKey", "s", "bucketName", "b")));

        assertThat(container.getProviderType()).isEqualTo("minio");

        // opt-in: initialization attempts the network and fails fast
        assertThatThrownBy(() -> new MinioStorageProvider()
                        .createContainer(config(Map.of(
                                "endpoint",
                                "http://127.0.0.1:1",
                                "accessKey",
                                "a",
                                "secretKey",
                                "s",
                                "bucketName",
                                "b",
                                "createBucketIfAbsent",
                                true))))
                .isInstanceOf(RuntimeException.class);
    }
}
