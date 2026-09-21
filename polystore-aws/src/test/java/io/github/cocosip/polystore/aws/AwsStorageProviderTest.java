package io.github.cocosip.polystore.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.StorageContainer;
import io.github.cocosip.polystore.UrlArgs;
import java.time.Duration;
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
        return new AwsStorageProvider()
                .createContainer(ContainerConfiguration.builder()
                        .name("archive")
                        .type("aws")
                        .properties(properties)
                        .build());
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
        String url = container(STATIC).getUrl("2024/dump.sql");

        assertThat(url).startsWith("https://my-bucket.s3.amazonaws.com/");
        assertThat(url).contains("X-Amz-Signature");
    }

    @Test
    void explicitUrlArgsExpiryShouldOverrideContainerDefault() {
        String url = container(STATIC)
                .getUrl(
                        "a.txt",
                        UrlArgs.builder().expiry(Duration.ofMinutes(10)).build());

        assertThat(url).contains("X-Amz-Expires=600");
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

        assertThatThrownBy(() -> provider.createContainer(ContainerConfiguration.builder()
                        .name("c")
                        .type("aws")
                        .properties(Map.of("containerName", "b", "accessKeyId", "a", "secretAccessKey", "s"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("region");
        assertThatThrownBy(() -> provider.createContainer(ContainerConfiguration.builder()
                        .name("c")
                        .type("aws")
                        .properties(Map.of("region", "us-east-1", "accessKeyId", "a", "secretAccessKey", "s"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("containerName");
        assertThatThrownBy(() -> provider.createContainer(ContainerConfiguration.builder()
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

        assertThatThrownBy(() -> provider.createContainer(ContainerConfiguration.builder()
                        .name("c")
                        .type("aws")
                        .properties(missingName)
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("name");

        Map<String, Object> missingPolicy = new HashMap<>(missingName);
        missingPolicy.put("name", "session");
        assertThatThrownBy(() -> provider.createContainer(ContainerConfiguration.builder()
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

        assertThat(container.getUrl("a.txt")).startsWith("https://my-bucket.s3.amazonaws.com/");
    }
}
