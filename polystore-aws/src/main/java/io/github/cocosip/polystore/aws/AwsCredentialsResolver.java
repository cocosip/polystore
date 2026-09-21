package io.github.cocosip.polystore.aws;

import io.github.cocosip.polystore.exception.StorageOperationException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.services.sts.model.GetFederationTokenRequest;
import software.amazon.awssdk.services.sts.model.GetSessionTokenRequest;

/**
 * Resolves the AWS credentials of one {@code aws} container, mirroring the credential selection
 * order of the reference {@code DefaultAmazonS3ClientFactory}:
 *
 * <ol>
 *   <li>{@code useCredentials} — AWS profile (when {@code profileName} is set) or the default
 *       provider chain</li>
 *   <li>{@code useTemporaryCredentials} — STS {@code GetSessionToken} session credentials</li>
 *   <li>{@code useTemporaryFederatedCredentials} — STS {@code GetFederationToken} credentials</li>
 *   <li>otherwise the static {@code accessKeyId} / {@code secretAccessKey} pair</li>
 * </ol>
 *
 * <p>Temporary credentials are cached process-wide by {@code temporaryCredentialsCacheKey} and are
 * refreshed shortly before they expire; resolution is lazy, so creating a container never performs
 * a network call. Missing required parameters raise {@link IllegalStateException} — a configuration
 * problem, not a storage failure.</p>
 */
final class AwsCredentialsResolver {

    /** Fallback validity used when STS reports no expiration and no duration was configured. */
    private static final Duration FALLBACK_TTL = Duration.ofMinutes(15);

    /** Refresh margin, so an in-flight request never uses a credential that expires mid-call. */
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(10);

    private static final ConcurrentMap<String, AwsCredentialsProvider> TEMPORARY_PROVIDERS = new ConcurrentHashMap<>();

    private AwsCredentialsResolver() {}

    /**
     * Creates the credentials provider matching the configured credential mode.
     *
     * @param options container parameters, never {@code null}
     * @return credentials provider, never {@code null}
     * @throws IllegalStateException if the selected mode lacks its required parameters
     */
    static AwsCredentialsProvider resolve(AwsStorageConfiguration options) {
        if (options.useCredentials()) {
            return profileOrDefault(options);
        }
        if (options.useTemporaryCredentials()) {
            return temporary(options, () -> sessionToken(options));
        }
        if (options.useTemporaryFederatedCredentials()) {
            requireFederationParameters(options);
            return temporary(options, () -> federationToken(options));
        }
        return staticCredentials(options);
    }

    private static AwsCredentialsProvider temporary(
            AwsStorageConfiguration options, Supplier<AwsSessionCredentials> loader) {
        return TEMPORARY_PROVIDERS.computeIfAbsent(
                options.temporaryCredentialsCacheKey(), key -> new CachingSessionCredentialsProvider(loader));
    }

    private static AwsCredentialsProvider staticCredentials(AwsStorageConfiguration options) {
        if (isBlank(options.accessKeyId()) || isBlank(options.secretAccessKey())) {
            throw new IllegalStateException("Missing required storage parameter 'accessKeyId'/'secretAccessKey'"
                    + " for the aws provider; use an explicit key pair, 'useCredentials',"
                    + " 'useTemporaryCredentials' or 'useTemporaryFederatedCredentials'");
        }
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(options.accessKeyId(), options.secretAccessKey()));
    }

    private static AwsCredentialsProvider profileOrDefault(AwsStorageConfiguration options) {
        if (isBlank(options.profileName())) {
            return DefaultCredentialsProvider.create();
        }
        ProfileCredentialsProvider.Builder builder =
                ProfileCredentialsProvider.builder().profileName(options.profileName());
        if (!isBlank(options.profilesLocation())) {
            builder.profileFile(profileFile(options.profilesLocation()));
        }
        return builder.build();
    }

    private static AwsCredentialsProvider baseCredentials(AwsStorageConfiguration options) {
        if (!isBlank(options.accessKeyId()) && !isBlank(options.secretAccessKey())) {
            return staticCredentials(options);
        }
        return profileOrDefault(options);
    }

    private static StsClient stsClient(AwsStorageConfiguration options) {
        return StsClient.builder()
                .region(Region.of(options.region()))
                .credentialsProvider(baseCredentials(options))
                .build();
    }

    private static AwsSessionCredentials sessionToken(AwsStorageConfiguration options) {
        GetSessionTokenRequest.Builder request = GetSessionTokenRequest.builder();
        if (options.durationSeconds() > 0) {
            request.durationSeconds(options.durationSeconds());
        }
        try (StsClient sts = stsClient(options)) {
            return toSessionCredentials(sts.getSessionToken(request.build()).credentials());
        } catch (RuntimeException e) {
            throw new StorageOperationException("Failed to obtain AWS temporary credentials via GetSessionToken", e);
        }
    }

    private static AwsSessionCredentials federationToken(AwsStorageConfiguration options) {
        GetFederationTokenRequest.Builder request =
                GetFederationTokenRequest.builder().name(options.name()).policy(options.policy());
        if (options.durationSeconds() > 0) {
            request.durationSeconds(options.durationSeconds());
        }
        try (StsClient sts = stsClient(options)) {
            return toSessionCredentials(sts.getFederationToken(request.build()).credentials());
        } catch (RuntimeException e) {
            throw new StorageOperationException(
                    "Failed to obtain AWS federated temporary credentials via GetFederationToken", e);
        }
    }

    private static AwsSessionCredentials toSessionCredentials(Credentials credentials) {
        return AwsSessionCredentials.builder()
                .accessKeyId(credentials.accessKeyId())
                .secretAccessKey(credentials.secretAccessKey())
                .sessionToken(credentials.sessionToken())
                .expirationTime(credentials.expiration())
                .build();
    }

    private static void requireFederationParameters(AwsStorageConfiguration options) {
        if (isBlank(options.name())) {
            throw new IllegalStateException(
                    "Missing required storage parameter 'name' for AWS federated temporary credentials");
        }
        if (isBlank(options.policy())) {
            throw new IllegalStateException(
                    "Missing required storage parameter 'policy' for AWS federated temporary credentials");
        }
    }

    /**
     * Builds the profile file from the configured location. A directory is read as an AWS profile
     * directory (its {@code credentials} and {@code config} files are aggregated); a file is read
     * according to its name, exactly like the AWS SDK does for {@code AWS_SHARED_CREDENTIALS_FILE}
     * and {@code AWS_CONFIG_FILE}.
     */
    private static ProfileFile profileFile(String location) {
        Path path = Paths.get(location);
        if (Files.isDirectory(path)) {
            Path credentialsFile = path.resolve("credentials");
            Path configFile = path.resolve("config");
            ProfileFile.Aggregator aggregator = ProfileFile.aggregator();
            boolean added = false;
            if (Files.exists(credentialsFile)) {
                aggregator.addFile(ProfileFile.builder()
                        .content(credentialsFile)
                        .type(ProfileFile.Type.CREDENTIALS)
                        .build());
                added = true;
            }
            if (Files.exists(configFile)) {
                aggregator.addFile(ProfileFile.builder()
                        .content(configFile)
                        .type(ProfileFile.Type.CONFIGURATION)
                        .build());
                added = true;
            }
            if (!added) {
                throw new IllegalStateException(
                        "AWS profilesLocation directory contains neither 'credentials' nor 'config': " + location);
            }
            return aggregator.build();
        }
        if (!Files.exists(path)) {
            throw new IllegalStateException("AWS profilesLocation does not exist: " + location);
        }
        Path fileName = path.getFileName();
        boolean credentialsFile = fileName != null && "credentials".equals(fileName.toString());
        return ProfileFile.builder()
                .content(path)
                .type(credentialsFile ? ProfileFile.Type.CREDENTIALS : ProfileFile.Type.CONFIGURATION)
                .build();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Caches one set of session credentials and reloads them when they are about to expire.
     */
    private static final class CachingSessionCredentialsProvider implements AwsCredentialsProvider {

        private final Supplier<AwsSessionCredentials> loader;
        private volatile CachedCredentials cached;

        private CachingSessionCredentialsProvider(Supplier<AwsSessionCredentials> loader) {
            this.loader = loader;
        }

        @Override
        public AwsCredentials resolveCredentials() {
            CachedCredentials current = cached;
            if (current != null && current.expiresAt().isAfter(Instant.now())) {
                return current.credentials();
            }
            AwsSessionCredentials loaded = loader.get();
            cached = new CachedCredentials(
                    loaded,
                    loaded.expirationTime()
                            .map(expiry -> expiry.minus(EXPIRY_MARGIN))
                            .orElseGet(() -> Instant.now().plus(FALLBACK_TTL)));
            return loaded;
        }

        /** One cached credential set together with its refresh instant. */
        private record CachedCredentials(AwsSessionCredentials credentials, Instant expiresAt) {}
    }
}
