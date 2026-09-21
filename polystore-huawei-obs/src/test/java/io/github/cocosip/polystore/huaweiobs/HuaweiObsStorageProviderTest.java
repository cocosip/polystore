package io.github.cocosip.polystore.huaweiobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.DefaultStorageContainer;
import io.github.cocosip.polystore.StorageContainer;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HuaweiObsStorageProviderTest {

    private static final Map<String, Object> CONFIG = Map.of(
            "endpoint", "obs.cn-north-4.myhuaweicloud.com",
            "accessKeyId", "ak",
            "accessKeySecret", "sk",
            "bucketName", "dicom");

    private static StorageContainer build(Map<String, Object> properties) {
        ContainerConfiguration configuration = ContainerConfiguration.builder()
                .name("obs")
                .type("huawei-obs")
                .properties(properties)
                .build();
        HuaweiObsStorageProvider provider = new HuaweiObsStorageProvider();
        return DefaultStorageContainer.from(configuration, provider.createBackend(configuration));
    }

    private static Map<String, Object> with(String key, Object value) {
        Map<String, Object> properties = new HashMap<>(CONFIG);
        properties.put(key, value);
        return properties;
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

        String url = container.getAccessUrl("2024/scan.dcm", Instant.now().plusSeconds(61), false);

        assertThat(url).contains("obs.cn-north-4.myhuaweicloud.com");
        assertThat(url).contains("Signature=");
        assertThat(url).contains("Expires=");
    }

    @Test
    void explicitExpiryShouldBeAccepted() {
        StorageContainer container = build(CONFIG);

        String url = container.getAccessUrl("a.txt", Instant.now().plusSeconds(601), false);

        assertThat(url).isNotBlank();
    }

    @Test
    void createContainerIfNotExistsShouldNotTouchTheNetworkAtConstruction() {
        // the bucket is created lazily on save, so an unreachable endpoint must not fail container
        // construction even with the flag enabled
        Map<String, Object> properties = with("endpoint", "http://127.0.0.1:1");
        properties.put("createContainerIfNotExists", true);

        assertThat(build(properties).getProviderType()).isEqualTo("huawei-obs");
    }

    @Test
    void canonicalKeysShouldResolveRegardlessOfCaseAndSeparators() {
        StorageContainer container = build(Map.of(
                "End-Point", "obs.cn-north-4.myhuaweicloud.com",
                "Bucket_Name", "dicom",
                "Access-Key-Id", "ak",
                "ACCESS_KEY_SECRET", "sk",
                "Create-Container-If-Not-Exists", true));

        assertThat(container.getAccessUrl("a.txt", Instant.now().plusSeconds(61), false))
                .contains("Signature=");
    }

    @Test
    void sharpAbpQualifiedKeysShouldResolve() {
        StorageContainer container = build(Map.of(
                "Obs.EndPoint", "obs.cn-north-4.myhuaweicloud.com",
                "Obs.BucketName", "dicom",
                "Obs.AccessKeyId", "ak",
                "Obs.AccessKeySecret", "sk",
                "Obs.CreateContainerIfNotExists", true));

        assertThat(container.getProviderType()).isEqualTo("huawei-obs");
        assertThat(container.getAccessUrl("a.txt", Instant.now().plusSeconds(61), false))
                .contains("Signature=");
    }

    @Test
    void missingRequiredParametersShouldBeRejected() {
        HuaweiObsStorageProvider provider = new HuaweiObsStorageProvider();

        assertThatThrownBy(() -> provider.createBackend(ContainerConfiguration.builder()
                        .name("c")
                        .type("huawei-obs")
                        .properties(Map.of("accessKeyId", "a", "accessKeySecret", "s", "bucketName", "b"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("endpoint");
        assertThatThrownBy(() -> provider.createBackend(ContainerConfiguration.builder()
                        .name("c")
                        .type("huawei-obs")
                        .properties(Map.of("endpoint", "e", "accessKeyId", "a", "accessKeySecret", "s"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bucketName");
        assertThatThrownBy(() -> provider.createBackend(ContainerConfiguration.builder()
                        .name("c")
                        .type("huawei-obs")
                        .properties(Map.of("endpoint", "e", "accessKeySecret", "s", "bucketName", "b"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accessKeyId");
        assertThatThrownBy(() -> provider.createBackend(ContainerConfiguration.builder()
                        .name("c")
                        .type("huawei-obs")
                        .properties(Map.of("endpoint", "e", "accessKeyId", "a", "bucketName", "b"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accessKeySecret");
    }
}
