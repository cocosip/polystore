package io.github.cocosip.polystore.aliyunoss;

import static org.assertj.core.api.Assertions.assertThat;

import com.aliyun.oss.common.auth.Credentials;
import io.github.cocosip.polystore.ContainerConfiguration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The provider must serve the current cached credentials per request and re-assume the role once
 * they expire, so a long-lived OSS client survives session rotations. Only the cache hit path is
 * exercised here; the AssumeRole call itself requires the STS endpoint.
 */
class AliyunOssStsCredentialsProviderTest {

    private static final String CACHE_KEY = "sts-provider-test/aliyun";

    @AfterEach
    void clearCache() {
        AliyunTemporaryCredentialsCache.clear();
    }

    @Test
    void shouldServeTheCachedCredentialsWithoutCallingAssumeRole() {
        AliyunTemporaryCredentialsCache.put(
                CACHE_KEY,
                new AliyunTemporaryCredentials("sts-ak", "sts-sk", "sts-token"),
                Instant.now().plusSeconds(300).toString(),
                0);

        Credentials credentials = provider().getCredentials();

        assertThat(credentials.getAccessKeyId()).isEqualTo("sts-ak");
        assertThat(credentials.getSecretAccessKey()).isEqualTo("sts-sk");
        assertThat(credentials.getSecurityToken()).isEqualTo("sts-token");
        assertThat(credentials.useSecurityToken()).isTrue();
    }

    private AliyunOssStsCredentialsProvider provider() {
        AliyunOssStorageConfiguration configuration =
                AliyunOssStorageConfiguration.from(ContainerConfiguration.builder()
                        .name("archive")
                        .type("aliyun-oss")
                        .properties(Map.of(
                                "endpoint", "oss-cn-hangzhou.aliyuncs.com",
                                "bucketName", "archive",
                                "accessKeyId", "LTAI",
                                "accessKeySecret", "secret",
                                "useSecurityTokenService", true,
                                "regionId", "cn-hangzhou",
                                "roleArn", "acs:ram::1234567890:role/polystore",
                                "roleSessionName", "polystore-session",
                                "temporaryCredentialsCacheKey", CACHE_KEY))
                        .build());
        return new AliyunOssStsCredentialsProvider(configuration);
    }
}
