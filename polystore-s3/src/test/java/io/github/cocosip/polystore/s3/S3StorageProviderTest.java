package io.github.cocosip.polystore.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.StorageContainer;
import io.github.cocosip.polystore.UrlArgs;
import io.github.cocosip.polystore.exception.StorageOperationException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class S3StorageProviderTest {

    private static final Map<String, Object> AWS = Map.of(
            "region", "cn-northwest-1",
            "accessKeyId", "AKIAIOSFODNN7EXAMPLE",
            "secretAccessKey", "wJalrXUtnFEMI",
            "bucketName", "my-backup");

    private static final Map<String, Object> COMPATIBLE = Map.of(
            "region", "us-east-1",
            "accessKeyId", "ak",
            "secretAccessKey", "sk",
            "bucketName", "backup",
            "endpoint", "http://ceph.internal:7480",
            "pathStyleAccess", true,
            "urlExpiry", 60);

    @Test
    void providerTypeShouldBeS3() {
        assertThat(new S3StorageProvider().getType()).isEqualTo("s3");
    }

    @Test
    void shouldBuildContainerFromAwsConfiguration() {
        StorageContainer container = new S3StorageProvider()
                .createContainer(ContainerConfiguration.builder()
                        .name("backup")
                        .type("s3")
                        .properties(AWS)
                        .build());

        assertThat(container.getProviderType()).isEqualTo("s3");
        assertThat(container.getInfo().getName()).isEqualTo("backup");
    }

    @Test
    void presignedUrlShouldTargetTheVirtualHostedByDefault() {
        StorageContainer container = new S3StorageProvider()
                .createContainer(ContainerConfiguration.builder()
                        .name("backup")
                        .type("s3")
                        .properties(AWS)
                        .build());

        String url = container.getUrl("2024/dump.sql");

        assertThat(url).startsWith("https://my-backup.s3.");
        assertThat(url).contains("X-Amz-Signature");
    }

    @Test
    void pathStyleShouldAddressEndpointWithBucketInPath() {
        StorageContainer container = new S3StorageProvider()
                .createContainer(ContainerConfiguration.builder()
                        .name("backup")
                        .type("s3")
                        .properties(COMPATIBLE)
                        .build());

        String url = container.getUrl("a.txt");

        assertThat(url).startsWith("http://ceph.internal:7480/backup/a.txt");
        assertThat(url).contains("X-Amz-Expires=60");
    }

    @Test
    void explicitUrlArgsExpiryShouldOverrideContainerDefault() {
        StorageContainer container = new S3StorageProvider()
                .createContainer(ContainerConfiguration.builder()
                        .name("backup")
                        .type("s3")
                        .properties(COMPATIBLE)
                        .build());

        String url = container.getUrl(
                "a.txt", UrlArgs.builder().expiry(Duration.ofMinutes(10)).build());

        assertThat(url).contains("X-Amz-Expires=600");
    }

    @Test
    void missingRequiredParametersShouldBeRejected() {
        S3StorageProvider provider = new S3StorageProvider();

        assertThatThrownBy(() -> provider.createContainer(ContainerConfiguration.builder()
                        .name("c")
                        .type("s3")
                        .properties(Map.of("accessKeyId", "a", "secretAccessKey", "s", "bucketName", "b"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("region");
        assertThatThrownBy(() -> provider.createContainer(ContainerConfiguration.builder()
                        .name("c")
                        .type("s3")
                        .properties(Map.of("region", "us-east-1", "secretAccessKey", "s", "bucketName", "b"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accessKeyId");
    }

    @Test
    void invalidEndpointShouldBeRejected() {
        assertThatThrownBy(() -> new S3StorageProvider()
                        .createContainer(ContainerConfiguration.builder()
                                .name("c")
                                .type("s3")
                                .properties(Map.of(
                                        "region",
                                        "us-east-1",
                                        "accessKeyId",
                                        "a",
                                        "secretAccessKey",
                                        "s",
                                        "bucketName",
                                        "b",
                                        "endpoint",
                                        "::::not-a-uri"))
                                .build()))
                .isInstanceOf(StorageOperationException.class)
                .hasMessageContaining("endpoint");
    }
}
