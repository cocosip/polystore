package io.github.cocosip.polystore.aliyunoss;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Process-local, thread-safe cache of Aliyun STS temporary credentials, mirroring the distributed
 * cache of the reference {@code DefaultOssClientFactory}: entries are keyed by
 * {@code temporaryCredentialsCacheKey} and expire shortly before the credentials reported by
 * {@code AssumeRole} do.
 *
 * <p>The time to live is the reported credential expiration minus a ten-second safety margin, so a
 * request in flight never starts with credentials that are about to expire. When the response
 * carries no parsable expiration the configured {@code durationSeconds} is used instead, falling
 * back to the Aliyun STS default of one hour when the duration is left at {@code 0}. A non-positive
 * time to live means "do not cache".</p>
 */
final class AliyunTemporaryCredentialsCache {

    /** Safety margin subtracted from every credential lifetime. */
    static final int EXPIRY_MARGIN_SECONDS = 10;

    /** Aliyun STS default credential lifetime, used when no expiration and no duration are known. */
    static final int DEFAULT_DURATION_SECONDS = 3600;

    private static final ConcurrentMap<String, CacheEntry> CACHE = new ConcurrentHashMap<>();

    private AliyunTemporaryCredentialsCache() {}

    /**
     * Returns the cached credentials of the given cache key.
     *
     * @param cacheKey credential cache key, never {@code null}
     * @return cached credentials, or {@code null} when absent or expired
     */
    static AliyunTemporaryCredentials get(String cacheKey) {
        CacheEntry entry = CACHE.get(cacheKey);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired(System.currentTimeMillis())) {
            CACHE.remove(cacheKey, entry);
            return null;
        }
        return entry.credentials();
    }

    /**
     * Caches one set of temporary credentials until they are about to expire.
     *
     * @param cacheKey           credential cache key, never {@code null}
     * @param credentials        resolved temporary credentials, never {@code null}
     * @param expiration         expiration reported by {@code AssumeRole}, may be {@code null}
     * @param durationSeconds    configured credential validity, {@code 0} for the service default
     */
    static void put(String cacheKey, AliyunTemporaryCredentials credentials, String expiration, int durationSeconds) {
        long ttlSeconds = ttlSeconds(expiration, durationSeconds);
        if (ttlSeconds <= 0) {
            return;
        }
        CACHE.put(cacheKey, new CacheEntry(credentials, System.currentTimeMillis() + ttlSeconds * 1000L));
    }

    /** Removes every cached entry. */
    static void clear() {
        CACHE.clear();
    }

    /**
     * Computes the cache time to live of one credential set.
     *
     * @param expiration      expiration reported by {@code AssumeRole}, may be {@code null} or
     *                        unparsable
     * @param durationSeconds configured credential validity, {@code 0} for the service default
     * @return time to live in seconds, including the ten-second safety margin
     */
    static long ttlSeconds(String expiration, int durationSeconds) {
        Instant expirationInstant = parseInstant(expiration);
        if (expirationInstant != null) {
            return Duration.between(Instant.now(), expirationInstant).getSeconds() - EXPIRY_MARGIN_SECONDS;
        }
        int lifetimeSeconds = durationSeconds > 0 ? durationSeconds : DEFAULT_DURATION_SECONDS;
        return lifetimeSeconds - EXPIRY_MARGIN_SECONDS;
    }

    private static Instant parseInstant(String expiration) {
        if (expiration == null || expiration.trim().isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(expiration.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** One cached credential set together with its absolute expiry instant. */
    private record CacheEntry(AliyunTemporaryCredentials credentials, long expiresAtMillis) {

        private boolean isExpired(long nowMillis) {
            return nowMillis >= expiresAtMillis;
        }
    }
}
