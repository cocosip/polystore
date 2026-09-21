package io.github.cocosip.polystore.aliyunoss;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the time-to-live rules of the process-local STS credential cache.
 */
class AliyunTemporaryCredentialsCacheTest {

    private static final AliyunTemporaryCredentials CREDENTIALS =
            new AliyunTemporaryCredentials("STS.accessKeyId", "STS.accessKeySecret", "STS.securityToken");

    @AfterEach
    void clearCache() {
        AliyunTemporaryCredentialsCache.clear();
    }

    @Test
    void shouldCacheCredentialsUntilTenSecondsBeforeTheReportedExpiration() {
        String expiration = Instant.now().plusSeconds(600).toString();

        assertThat(AliyunTemporaryCredentialsCache.ttlSeconds(expiration, 0)).isBetween(585L, 590L);

        AliyunTemporaryCredentialsCache.put("cache-key", CREDENTIALS, expiration, 0);

        assertThat(AliyunTemporaryCredentialsCache.get("cache-key")).isEqualTo(CREDENTIALS);
    }

    @Test
    void ttlShouldFallBackToTheConfiguredDuration() {
        assertThat(AliyunTemporaryCredentialsCache.ttlSeconds(null, 900)).isEqualTo(890L);
        assertThat(AliyunTemporaryCredentialsCache.ttlSeconds("not-a-timestamp", 900))
                .isEqualTo(890L);
    }

    @Test
    void ttlShouldFallBackToTheServiceDefaultDuration() {
        assertThat(AliyunTemporaryCredentialsCache.ttlSeconds(null, 0)).isEqualTo(3590L);
    }

    @Test
    void shouldNotCacheAlreadyExpiredCredentials() {
        AliyunTemporaryCredentialsCache.put(
                "cache-key", CREDENTIALS, Instant.now().minusSeconds(60).toString(), 0);

        assertThat(AliyunTemporaryCredentialsCache.get("cache-key")).isNull();
    }

    @Test
    void shouldNotKnowUnknownCacheKeys() {
        assertThat(AliyunTemporaryCredentialsCache.get("missing")).isNull();
    }
}
