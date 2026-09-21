package io.github.cocosip.polystore.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.DefaultStorageContainer;
import io.github.cocosip.polystore.StorageContainer;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AwsStorageProviderTest {

    private static final Map<String, Object> STATIC = Map.of(
            "region", "us-east-1",
            "containerName", "my-bucket",
            "accessKeyId", "AKIAIOSFODNN7EXAMPLE",
            "secretAccessKey", "wJalrXUtnFEMI");

    private static StorageContainer container(Map<String, Object> properties) {
        ContainerConfiguration configuration = ContainerConfiguration.builder()
                .name("archive")
                .type("aws")
                .properties(properties)
                .build();
        AwsStorageProvider provider = new AwsStorageProvider();
        return DefaultStorageContainer.from(configuration, provider.createBackend(configuration));
    }

    private static Map<String, Object> withCreateContainer() {
        Map<String, Object> properties = new HashMap<>(STATIC);
        properties.put("createContainerIfNotExists", true);
        return properties;
    }

    @Test
    void providerTypeShouldBeAws() {
        assertThat(new AwsStorageProvider().getType()).isEqualTo("aws");
    }

    @Test
    void shouldBuildContainerFromStaticCredentials() {
        StorageContainer container = container(STATIC);

        assertThat(container.getName()).isEqualTo("archive");
        assertThat(container.getProviderType()).isEqualTo("aws");
    }

    @Test
    void presignedUrlShouldTargetAmazonS3() {
        String url =
                container(STATIC).getAccessUrl("2024/dump.sql", Instant.now().plusSeconds(601), false);

        assertThat(url).startsWith("https://my-bucket.s3.amazonaws.com/");
        assertThat(url).contains("X-Amz-Signature");
    }

    @Test
    void explicitUrlArgsExpiryShouldOverrideContainerDefault() {
        String url = container(STATIC).getAccessUrl("a.txt", Instant.now().plusSeconds(601), false);

        assertThat(url).containsPattern("X-Amz-Expires=60[01]");
    }

    @Test
    void createContainerIfNotExistsShouldNotTouchTheNetworkAtConstruction() {
        // the bucket is created lazily on save, so an unreachable region/credentials pair must not
        // fail container construction
        assertThat(container(withCreateContainer()).getProviderType()).isEqualTo("aws");
    }

    @Test
    void credentialSwitchShouldNotRequireStaticKeys() {
        assertThat(container(Map.of("region", "us-east-1", "containerName", "b", "useCredentials", true))
                        .getProviderType())
                .isEqualTo("aws");
        assertThat(container(Map.of("region", "us-east-1", "containerName", "b", "useTemporaryCredentials", true))
                        .getProviderType())
                .isEqualTo("aws");
        Map<String, Object> federated = new HashMap<>();
        federated.put("region", "us-east-1");
        federated.put("containerName", "b");
        federated.put("useTemporaryFederatedCredentials", true);
        federated.put("name", "session");
        federated.put("policy", "{\"Version\":\"2012-10-17\"}");
        assertThat(container(federated).getProviderType()).isEqualTo("aws");
    }

    @Test
    void missingRequiredParametersShouldBeRejected() {
        AwsStorageProvider provider = new AwsStorageProvider();

        assertThatThrownBy(() -> provider.createBackend(ContainerConfiguration.builder()
                        .name("c")
                        .type("aws")
                        .properties(Map.of("containerName", "b", "accessKeyId", "a", "secretAccessKey", "s"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("region");
        assertThatThrownBy(() -> provider.createBackend(ContainerConfiguration.builder()
                        .name("c")
                        .type("aws")
                        .properties(Map.of("region", "us-east-1", "accessKeyId", "a", "secretAccessKey", "s"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("containerName");
        assertThatThrownBy(() -> provider.createBackend(ContainerConfiguration.builder()
                        .name("c")
                        .type("aws")
                        .properties(Map.of("region", "us-east-1", "containerName", "b"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accessKeyId");
    }

    @Test
    void federatedCredentialsShouldRequireNameAndPolicy() {
        AwsStorageProvider provider = new AwsStorageProvider();
        Map<String, Object> missingName = new HashMap<>();
        missingName.put("region", "us-east-1");
        missingName.put("containerName", "b");
        missingName.put("useTemporaryFederatedCredentials", true);

        assertThatThrownBy(() -> provider.createBackend(ContainerConfiguration.builder()
                        .name("c")
                        .type("aws")
                        .properties(missingName)
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("name");

        Map<String, Object> missingPolicy = new HashMap<>(missingName);
        missingPolicy.put("name", "session");
        assertThatThrownBy(() -> provider.createBackend(ContainerConfiguration.builder()
                        .name("c")
                        .type("aws")
                        .properties(missingPolicy)
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("policy");
    }

    @Test
    void sharpAbpStyleKeysShouldBeMatchedCaseInsensitively() {
        StorageContainer container = container(Map.of(
                "Aws.Region", "us-east-1",
                "Aws.ContainerName", "my-bucket",
                "Aws.AccessKeyId", "AKIAIOSFODNN7EXAMPLE",
                "Aws.SecretAccessKey", "wJalrXUtnFEMI"));

        assertThat(container.getAccessUrl("a.txt", Instant.now().plusSeconds(61), false))
                .startsWith("https://my-bucket.s3.amazonaws.com/");
    }
}
