package io.github.cocosip.polystore.minio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.DefaultStorageContainer;
import io.github.cocosip.polystore.StorageContainer;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MinioStorageProviderTest {

    private static final Map<String, Object> FULL = Map.of(
            "endPoint", "minio.internal:9000",
            "accessKey", "admin",
            "secretKey", "secret",
            "bucketName", "dicom",
            "region", "us-east-1");

    private static ContainerConfiguration configuration(Map<String, Object> properties) {
        return ContainerConfiguration.builder()
                .name("dicom")
                .type("minio")
                .properties(properties)
                .build();
    }

    private static StorageContainer container(Map<String, Object> properties) {
        ContainerConfiguration configuration = configuration(properties);
        MinioStorageProvider provider = new MinioStorageProvider();
        return DefaultStorageContainer.from(configuration, provider.createBackend(configuration));
    }

    @Test
    void providerTypeShouldBeMinio() {
        assertThat(new MinioStorageProvider().getType()).isEqualTo("minio");
    }

    @Test
    void shouldBuildContainerFromSharpAbpConfiguration() {
        StorageContainer container = container(FULL);

        assertThat(container.getName()).isEqualTo("dicom");
        assertThat(container.getProviderType()).isEqualTo("minio");
        assertThat(container.getInfo().getProviderType()).isEqualTo("minio");
    }

    @Test
    void presignedUrlShouldBeComputedOfflineForTheRequestedExpiry() {
        String url = container(FULL).getAccessUrl("2024/scan.dcm", Instant.now().plusSeconds(61), false);

        assertThat(url).startsWith("http://minio.internal:9000/dicom/2024/scan.dcm");
        assertThat(url).contains("X-Amz-Signature=");
        assertThat(url).containsPattern("X-Amz-Expires=6[01]");
    }

    @Test
    void unknownUrlExpiryKeyShouldBeIgnoredWithoutBreakingParsing() {
        Map<String, Object> properties = new HashMap<>(FULL);
        properties.put("urlExpiry", 60);

        assertThat(container(properties).getProviderType()).isEqualTo("minio");
    }

    @Test
    void withSslShouldForceHttps() {
        Map<String, Object> properties = new HashMap<>(FULL);
        properties.put("withSSL", true);

        assertThat(container(properties).getAccessUrl("a.txt", Instant.now().plusSeconds(61), false))
                .startsWith("https://minio.internal:9000/");
    }

    @Test
    void httpShouldBeTheDefaultProtocol() {
        assertThat(container(FULL).getAccessUrl("a.txt", Instant.now().plusSeconds(61), false))
                .startsWith("http://minio.internal:9000/");
    }

    @Test
    void endPointSchemeShouldWinOverWithSsl() {
        Map<String, Object> properties = new HashMap<>(FULL);
        properties.put("endPoint", "https://minio.internal:9000");
        properties.put("withSSL", false);

        assertThat(container(properties).getAccessUrl("a.txt", Instant.now().plusSeconds(61), false))
                .startsWith("https://minio.internal:9000/");
    }

    @Test
    void missingRequiredParametersShouldBeRejected() {
        MinioStorageProvider provider = new MinioStorageProvider();

        assertThatThrownBy(() -> provider.createBackend(ContainerConfiguration.builder()
                        .name("c")
                        .type("minio")
                        .properties(Map.of("accessKey", "a", "secretKey", "s", "bucketName", "b"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("endPoint");
        assertThatThrownBy(() -> provider.createBackend(ContainerConfiguration.builder()
                        .name("c")
                        .type("minio")
                        .properties(Map.of("endPoint", "http://x", "secretKey", "s", "bucketName", "b"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accessKey");
        assertThatThrownBy(() -> provider.createBackend(ContainerConfiguration.builder()
                        .name("c")
                        .type("minio")
                        .properties(Map.of("endPoint", "http://x", "accessKey", "a", "secretKey", "s"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bucketName");
    }

    @Test
    void createBucketIfNotExistsShouldNotTouchTheNetworkAtConstruction() {
        Map<String, Object> properties = new HashMap<>(FULL);
        properties.put("endPoint", "127.0.0.1:1");
        properties.put("createBucketIfNotExists", true);

        assertThat(container(properties).getProviderType()).isEqualTo("minio");
    }

    @Test
    void sharpAbpQualifiedKeysShouldBeAccepted() {
        StorageContainer container = container(Map.of(
                "Minio.EndPoint", "minio.internal:9000",
                "Minio.AccessKey", "admin",
                "Minio.SecretKey", "secret",
                "Minio.BucketName", "dicom",
                "Minio.WithSSL", true));

        assertThat(container.getAccessUrl("a.txt", Instant.now().plusSeconds(61), false))
                .startsWith("https://minio.internal:9000/dicom/a.txt");
    }
}
