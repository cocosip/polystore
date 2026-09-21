package io.github.cocosip.polystore.aliyunoss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.StorageContainer;
import io.github.cocosip.polystore.UrlArgs;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AliyunOssStorageProviderTest {

    private static final Map<String, Object> STATIC = Map.of(
            "endpoint", "oss-cn-hangzhou.aliyuncs.com",
            "accessKeyId", "LTAI",
            "accessKeySecret", "secret",
            "bucketName", "archive");

    private static StorageContainer build(Map<String, Object> properties) {
        return new AliyunOssStorageProvider()
                .createContainer(ContainerConfiguration.builder()
                        .name("archive")
                        .type("aliyun-oss")
                        .properties(properties)
                        .build());
    }

    private static Map<String, Object> with(String key, Object value) {
        Map<String, Object> properties = new HashMap<>(STATIC);
        properties.put(key, value);
        return properties;
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
        StorageContainer container = build(with("urlExpiry", 120));

        String url = container.getUrl("2024/report.pdf");

        assertThat(url).contains("Signature=");
        assertThat(url).contains("OSSAccessKeyId=LTAI");
        assertThat(url).contains("Expires=");
        String urlOverride = container.getUrl(
                "2024/report.pdf",
                UrlArgs.builder().expiry(Duration.ofMinutes(5)).build());
        assertThat(urlOverride).isNotBlank();
    }

    @Test
    void useInternalShouldRewriteAliyuncsEndpointsOnly() {
        String url = build(with("useInternal", true)).getUrl("a.txt");
        assertThat(url).contains("-internal.aliyuncs.com");

        Map<String, Object> external = new HashMap<>(STATIC);
        external.put("endpoint", "http://private-oss.example.com");
        external.put("useInternal", true);
        assertThat(build(external).getUrl("a.txt")).contains("private-oss.example.com");
    }

    @Test
    void createContainerIfNotExistsShouldNotTouchTheNetworkAtConstruction() {
        // the bucket is created lazily on save, so an unreachable endpoint must not fail container
        // construction even with the flag enabled
        Map<String, Object> properties = new HashMap<>(STATIC);
        properties.put("endpoint", "http://127.0.0.1:1");
        properties.put("createContainerIfNotExists", true);

        assertThat(build(properties).getProviderType()).isEqualTo("aliyun-oss");
    }

    @Test
    void securityTokenServiceShouldNotResolveCredentialsAtConstruction() {
        // AssumeRole is only sent on first use, so an unreachable endpoint and region must not fail
        // container construction
        Map<String, Object> properties = new HashMap<>(STATIC);
        properties.put("endpoint", "http://127.0.0.1:1");
        properties.put("useSecurityTokenService", true);
        properties.put("regionId", "cn-hangzhou");
        properties.put("roleArn", "acs:ram::1234567890:role/polystore");
        properties.put("roleSessionName", "polystore-session");
        properties.put("durationSeconds", 900);

        StorageContainer container = build(properties);

        assertThat(container.getProviderType()).isEqualTo("aliyun-oss");
        assertThat(container.getInfo().getName()).isEqualTo("archive");
    }

    @Test
    void securityTokenServiceShouldRequireRoleArnAndRoleSessionName() {
        AliyunOssStorageProvider provider = new AliyunOssStorageProvider();

        Map<String, Object> missingRoleArn = new HashMap<>(STATIC);
        missingRoleArn.put("useSecurityTokenService", true);
        missingRoleArn.put("roleSessionName", "polystore-session");
        assertThatThrownBy(() -> provider.createContainer(ContainerConfiguration.builder()
                        .name("archive")
                        .type("aliyun-oss")
                        .properties(missingRoleArn)
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("roleArn");

        Map<String, Object> missingSessionName = new HashMap<>(STATIC);
        missingSessionName.put("useSecurityTokenService", true);
        missingSessionName.put("roleArn", "acs:ram::1234567890:role/polystore");
        assertThatThrownBy(() -> provider.createContainer(ContainerConfiguration.builder()
                        .name("archive")
                        .type("aliyun-oss")
                        .properties(missingSessionName)
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("roleSessionName");
    }

    @Test
    void securityTokenServiceShouldRequireRegionId() {
        AliyunOssStorageProvider provider = new AliyunOssStorageProvider();

        Map<String, Object> properties = new HashMap<>(STATIC);
        properties.put("useSecurityTokenService", true);
        properties.put("roleArn", "acs:ram::1234567890:role/polystore");
        properties.put("roleSessionName", "polystore-session");

        assertThatThrownBy(() -> provider.createContainer(ContainerConfiguration.builder()
                        .name("archive")
                        .type("aliyun-oss")
                        .properties(properties)
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("regionId");
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
        assertThatThrownBy(() -> provider.createContainer(ContainerConfiguration.builder()
                        .name("c")
                        .type("aliyun-oss")
                        .properties(Map.of("endpoint", "e", "accessKeyId", "a", "bucketName", "b"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accessKeySecret");
        assertThatThrownBy(() -> provider.createContainer(ContainerConfiguration.builder()
                        .name("c")
                        .type("aliyun-oss")
                        .properties(Map.of("endpoint", "e", "accessKeyId", "a", "accessKeySecret", "s"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bucketName");
    }

    @Test
    void canonicalKeysShouldResolveRegardlessOfCaseAndSeparators() {
        StorageContainer container = build(Map.of(
                "EndPoint", "oss-cn-hangzhou.aliyuncs.com",
                "Bucket_Name", "archive",
                "Access-Key-Id", "LTAI",
                "ACCESS_KEY_SECRET", "secret",
                "Use-Security-Token-Service", false,
                "Create-Container-If-Not-Exists", true,
                "Temporary-Credentials-Cache-Key", "shared/aliyun"));

        assertThat(container.getUrl("a.txt")).contains("OSSAccessKeyId=LTAI");
    }

    @Test
    void sharpAbpQualifiedKeysShouldResolve() {
        StorageContainer container = build(Map.of(
                "Aliyun.EndPoint", "oss-cn-hangzhou.aliyuncs.com",
                "Aliyun.BucketName", "archive",
                "Aliyun.AccessKeyId", "LTAI",
                "Aliyun.AccessKeySecret", "secret",
                "Aliyun.RegionId", "cn-hangzhou",
                "Aliyun.DurationSeconds", 900,
                "Aliyun.UseSecurityTokenService", false,
                "Aliyun.CreateContainerIfNotExists", true,
                "Aliyun.TemporaryCredentialsCacheKey", "shared/aliyun"));

        assertThat(container.getProviderType()).isEqualTo("aliyun-oss");
        assertThat(container.getUrl("a.txt")).contains("OSSAccessKeyId=LTAI");
    }
}
