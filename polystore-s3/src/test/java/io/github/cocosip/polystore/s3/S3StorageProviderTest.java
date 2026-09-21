package io.github.cocosip.polystore.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.DefaultStorageContainer;
import io.github.cocosip.polystore.StorageContainer;
import io.github.cocosip.polystore.exception.StorageOperationException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class S3StorageProviderTest {

    private static final Map<String, Object> PATH_STYLE = Map.of(
            "serverUrl", "http://ceph.internal:7480",
            "accessKeyId", "ak",
            "secretAccessKey", "sk",
            "bucketName", "backup",
            "forcePathStyle", true,
            "urlExpiry", 60);

    private static StorageContainer container(Map<String, Object> properties) {
        ContainerConfiguration configuration = ContainerConfiguration.builder()
                .name("backup")
                .type("s3")
                .properties(properties)
                .build();
        S3StorageProvider provider = new S3StorageProvider();
        return DefaultStorageContainer.from(configuration, provider.createBackend(configuration));
    }

    @Test
    void providerTypeShouldBeS3() {
        assertThat(new S3StorageProvider().getType()).isEqualTo("s3");
    }

    @Test
    void shouldBuildContainerFromCompatibleStoreConfiguration() {
        StorageContainer container = container(PATH_STYLE);

        assertThat(container.getProviderType()).isEqualTo("s3");
        assertThat(container.getInfo().getName()).isEqualTo("backup");
    }

    @Test
    void serverUrlShouldBeRequired() {
        assertThatThrownBy(() -> new S3StorageProvider()
                        .createBackend(ContainerConfiguration.builder()
                                .name("backup")
                                .type("s3")
                                .properties(Map.of("accessKeyId", "ak", "secretAccessKey", "sk", "bucketName", "b"))
                                .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("serverUrl");
    }

    @Test
    void pathStyleShouldAddressServerUrlWithBucketInPath() {
        String url = container(PATH_STYLE).getAccessUrl("a.txt", Instant.now().plusSeconds(61), false);

        assertThat(url).startsWith("http://ceph.internal:7480/backup/a.txt");
        assertThat(url).containsPattern("X-Amz-Expires=6[01]");
    }

    @Test
    void protocolShouldSupplyTheMissingScheme() {
        Map<String, Object> properties = new HashMap<>(PATH_STYLE);
        properties.put("serverUrl", "ceph.internal:7480");
        properties.put("protocol", 2);

        assertThat(container(properties).getAccessUrl("a.txt", Instant.now().plusSeconds(61), false))
                .startsWith("https://ceph.internal:7480/backup/a.txt");
    }

    @Test
    void textualProtocolShouldBeAccepted() {
        Map<String, Object> properties = new HashMap<>(PATH_STYLE);
        properties.put("serverUrl", "ceph.internal:7480");
        properties.put("protocol", "HTTPS");

        assertThat(container(properties).getAccessUrl("a.txt", Instant.now().plusSeconds(61), false))
                .startsWith("https://ceph.internal:7480/backup/a.txt");
    }

    @Test
    void httpShouldBeTheDefaultProtocol() {
        Map<String, Object> properties = new HashMap<>(PATH_STYLE);
        properties.put("serverUrl", "ceph.internal:7480");

        assertThat(container(properties).getAccessUrl("a.txt", Instant.now().plusSeconds(61), false))
                .startsWith("http://ceph.internal:7480/backup/a.txt");
    }

    @Test
    void authenticationRegionShouldDefaultToUsEast1() {
        String url = container(PATH_STYLE).getAccessUrl("a.txt", Instant.now().plusSeconds(61), false);

        assertThat(url).contains("%2Fus-east-1%2Fs3%2Faws4_request");
    }

    @Test
    void authenticationRegionShouldBeHonoured() {
        Map<String, Object> properties = new HashMap<>(PATH_STYLE);
        properties.put("authenticationRegion", "cn-north-1");

        assertThat(container(properties).getAccessUrl("a.txt", Instant.now().plusSeconds(61), false))
                .contains("%2Fcn-north-1%2Fs3%2Faws4_request");
    }

    @Test
    void useChunkEncodingShouldBeOptional() {
        Map<String, Object> properties = new HashMap<>(PATH_STYLE);
        properties.put("useChunkEncoding", true);

        assertThat(container(properties).getProviderType()).isEqualTo("s3");
    }

    @Test
    void explicitUrlArgsExpiryShouldOverrideContainerDefault() {
        String url = container(PATH_STYLE).getAccessUrl("a.txt", Instant.now().plusSeconds(601), false);

        assertThat(url).containsPattern("X-Amz-Expires=60[01]");
    }

    @Test
    void createBucketIfNotExistsShouldNotTouchTheNetworkAtConstruction() {
        Map<String, Object> properties = new HashMap<>(PATH_STYLE);
        properties.put("serverUrl", "http://127.0.0.1:1");
        properties.put("createBucketIfNotExists", true);

        assertThat(container(properties).getProviderType()).isEqualTo("s3");
    }

    @Test
    void missingRequiredParametersShouldBeRejected() {
        S3StorageProvider provider = new S3StorageProvider();

        assertThatThrownBy(() -> provider.createBackend(ContainerConfiguration.builder()
                        .name("c")
                        .type("s3")
                        .properties(Map.of("serverUrl", "http://x", "secretAccessKey", "s", "bucketName", "b"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accessKeyId");
        assertThatThrownBy(() -> provider.createBackend(ContainerConfiguration.builder()
                        .name("c")
                        .type("s3")
                        .properties(Map.of("serverUrl", "http://x", "accessKeyId", "a", "secretAccessKey", "s"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bucketName");
    }

    @Test
    void invalidServerUrlShouldBeRejected() {
        assertThatThrownBy(() -> new S3StorageProvider()
                        .createBackend(ContainerConfiguration.builder()
                                .name("c")
                                .type("s3")
                                .properties(Map.of(
                                        "serverUrl",
                                        "::::not-a-uri",
                                        "accessKeyId",
                                        "a",
                                        "secretAccessKey",
                                        "s",
                                        "bucketName",
                                        "b"))
                                .build()))
                .isInstanceOf(StorageOperationException.class)
                .hasMessageContaining("serverUrl");
    }

    @Test
    void sharpAbpQualifiedKeysShouldBeAccepted() {
        StorageContainer container = container(Map.of(
                "S3.ServerUrl", "http://ceph.internal:7480",
                "S3.AccessKeyId", "ak",
                "S3.SecretAccessKey", "sk",
                "S3.BucketName", "backup",
                "S3.ForcePathStyle", true));

        assertThat(container.getAccessUrl("a.txt", Instant.now().plusSeconds(61), false))
                .startsWith("http://ceph.internal:7480/backup/a.txt");
    }
}
