package io.github.cocosip.polystore.ks3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ksyun.ks3.service.Ks3ClientConfig;
import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.DefaultStorageContainer;
import io.github.cocosip.polystore.StorageContainer;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Ks3StorageProviderTest {

    private static final Map<String, Object> FULL = Map.of(
            "endpoint", "ks3-cn-beijing.ksyuncs.com",
            "bucketName", "archive",
            "accessKey", "ak",
            "secretKey", "sk");

    private static StorageContainer container(Map<String, Object> properties) {
        ContainerConfiguration configuration = ContainerConfiguration.builder()
                .name("archive")
                .type("ks3")
                .properties(properties)
                .build();
        Ks3StorageProvider provider = new Ks3StorageProvider();
        return DefaultStorageContainer.from(configuration, provider.createBackend(configuration));
    }

    private static Map<String, Object> with(String key, Object value) {
        Map<String, Object> properties = new HashMap<>(FULL);
        properties.put(key, value);
        return properties;
    }

    private static Ks3StorageConfiguration configuration(Map<String, Object> properties) {
        return Ks3StorageConfiguration.from(ContainerConfiguration.builder()
                .name("archive")
                .type("ks3")
                .properties(properties)
                .build());
    }

    @Test
    void providerTypeShouldBeKs3() {
        assertThat(new Ks3StorageProvider().getType()).isEqualTo("ks3");
    }

    @Test
    void providerShouldAnswerToTheReferenceNameAsAlias() {
        assertThat(new Ks3StorageProvider().getAliases()).containsExactly("KS3");
    }

    @Test
    void shouldBuildContainerFromReferenceConfiguration() {
        StorageContainer container = container(FULL);

        assertThat(container.getName()).isEqualTo("archive");
        assertThat(container.getProviderType()).isEqualTo("ks3");
    }

    @Test
    void presignedUrlShouldBeComputedOffline() {
        String url =
                container(FULL).getAccessUrl("2024/report.pdf", Instant.now().plusSeconds(61), false);

        assertThat(url).contains("archive").contains("2024/report.pdf");
        assertThat(url).contains("Signature=").contains("Expires=");
    }

    @Test
    void explicitExpiryShouldBeAccepted() {
        String url = container(with("urlExpiry", 60))
                .getAccessUrl("a.txt", Instant.now().plusSeconds(301), false);

        assertThat(url).contains("Expires=");
    }

    @Test
    void httpShouldBeTheDefaultProtocol() {
        assertThat(container(FULL).getAccessUrl("a.txt", Instant.now().plusSeconds(61), false))
                .startsWith("http://");
    }

    @Test
    void protocolShouldSelectTheScheme() {
        assertThat(container(with("protocol", "https"))
                        .getAccessUrl("a.txt", Instant.now().plusSeconds(61), false))
                .startsWith("https://");
        assertThat(container(with("protocol", "HTTPS"))
                        .getAccessUrl("a.txt", Instant.now().plusSeconds(61), false))
                .startsWith("https://");
    }

    @Test
    void endpointSchemeShouldWinOverTheDefaultProtocolOnly() {
        assertThat(container(with("endpoint", "https://ks3-cn-beijing.ksyuncs.com"))
                        .getAccessUrl("a.txt", Instant.now().plusSeconds(61), false))
                .startsWith("https://");
        // an explicit protocol still wins over the endpoint scheme
        Map<String, Object> properties = new HashMap<>(FULL);
        properties.put("endpoint", "https://ks3-cn-beijing.ksyuncs.com");
        properties.put("protocol", "http");
        assertThat(container(properties).getAccessUrl("a.txt", Instant.now().plusSeconds(61), false))
                .startsWith("http://");
    }

    @Test
    void createContainerIfNotExistsShouldNotTouchTheNetworkAtConstruction() {
        Map<String, Object> properties = new HashMap<>(FULL);
        properties.put("createContainerIfNotExists", true);

        assertThat(container(properties).getProviderType()).isEqualTo("ks3");
    }

    @Test
    void missingRequiredParametersShouldBeRejected() {
        Ks3StorageProvider provider = new Ks3StorageProvider();

        assertThatThrownBy(() -> provider.createBackend(ContainerConfiguration.builder()
                        .name("c")
                        .type("ks3")
                        .properties(Map.of("bucketName", "b", "accessKey", "a", "secretKey", "s"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("endpoint");
        assertThatThrownBy(() -> provider.createBackend(ContainerConfiguration.builder()
                        .name("c")
                        .type("ks3")
                        .properties(Map.of("endpoint", "e", "accessKey", "a", "secretKey", "s"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bucketName");
        assertThatThrownBy(() -> provider.createBackend(ContainerConfiguration.builder()
                        .name("c")
                        .type("ks3")
                        .properties(Map.of("endpoint", "e", "bucketName", "b", "secretKey", "s"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accessKey");
        assertThatThrownBy(() -> provider.createBackend(ContainerConfiguration.builder()
                        .name("c")
                        .type("ks3")
                        .properties(Map.of("endpoint", "e", "bucketName", "b", "accessKey", "a"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secretKey");
    }

    @Test
    void invalidProtocolShouldBeRejected() {
        assertThatThrownBy(() -> container(with("protocol", "ftp")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("protocol");
    }

    @Test
    void invalidSignerVersionShouldBeRejected() {
        assertThatThrownBy(() -> container(with("signerVersion", "V5")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("signerVersion");
    }

    @Test
    void nonPositiveHttpClientValuesShouldBeRejected() {
        assertThatThrownBy(() -> container(with("maxConnections", 0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("maxConnections");
        assertThatThrownBy(() -> container(with("timeout", -1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    void ks3SignatureShouldBeTheDefaultAndAdjustable() {
        Ks3StorageConfiguration defaults = configuration(FULL);
        assertThat(defaults.useAwsSignature()).isFalse();
        assertThat(defaults.signerVersion()).isNull();

        assertThat(configuration(with("signerVersion", "v4")).signerVersion())
                .isEqualTo(Ks3ClientConfig.SignerVersion.V4);
        assertThat(configuration(with("useAwsSignature", true)).useAwsSignature())
                .isTrue();
    }

    @Test
    void httpClientSettingsShouldBeOptional() {
        Ks3StorageConfiguration defaults = configuration(FULL);
        assertThat(defaults.userAgent()).isNull();
        assertThat(defaults.maxConnections()).isNull();
        assertThat(defaults.timeout()).isNull();
        assertThat(defaults.readWriteTimeout()).isNull();

        Map<String, Object> properties = new HashMap<>(FULL);
        properties.put("userAgent", "polystore-test");
        properties.put("maxConnections", 32);
        properties.put("timeout", 5000);
        properties.put("readWriteTimeout", 6000);
        Ks3StorageConfiguration configured = configuration(properties);
        assertThat(configured.userAgent()).isEqualTo("polystore-test");
        assertThat(configured.maxConnections()).isEqualTo(32);
        assertThat(configured.timeout()).isEqualTo(5000);
        assertThat(configured.readWriteTimeout()).isEqualTo(6000);
    }

    @Test
    void sharpAbpQualifiedKeysShouldBeAccepted() {
        StorageContainer container = container(Map.of(
                "KS3.Endpoint", "ks3-cn-beijing.ksyuncs.com",
                "KS3.BucketName", "archive",
                "KS3.AccessKey", "ak",
                "KS3.SecretKey", "sk",
                "KS3.Protocol", "https"));

        assertThat(container.getAccessUrl("a.txt", Instant.now().plusSeconds(61), false))
                .startsWith("https://");
    }
}
