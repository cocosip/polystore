package io.github.cocosip.polystore.aliyunoss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.auth.sts.AssumeRoleRequest;
import com.aliyuncs.auth.sts.AssumeRoleResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.FormatType;
import com.aliyuncs.profile.DefaultProfile;
import io.github.cocosip.polystore.exception.StorageOperationException;
import java.util.function.Supplier;

/**
 * Builds OSS clients for the {@code aliyun-oss} provider, mirroring the reference
 * {@code DefaultOssClientFactory}: a sub-account key pair is used as is, while
 * {@code useSecurityTokenService} exchanges it for STS temporary credentials through
 * {@code AssumeRole} and signs OSS requests with the resulting security token.
 *
 * <p>Temporary credentials are cached process-wide by {@code temporaryCredentialsCacheKey} and are
 * reused until they are about to expire. Client creation is lazy, so building a container never
 * performs a network call — not even in STS mode, where the {@code AssumeRole} request is only sent
 * on first use.</p>
 */
final class AliyunOssClientFactory {

    private AliyunOssClientFactory() {}

    /**
     * Returns a memoizing supplier of the OSS client described by the given configuration.
     *
     * @param configuration container parameters, never {@code null}
     * @return supplier creating the client on first use and reusing it afterwards
     */
    static Supplier<OSS> lazyClient(AliyunOssStorageConfiguration configuration) {
        return new MemoizingOssClientSupplier(configuration);
    }

    /**
     * Builds the OSS client described by the given configuration, resolving STS temporary
     * credentials when {@code useSecurityTokenService} is enabled.
     *
     * @param configuration container parameters, never {@code null}
     * @return initialized OSS client, never {@code null}
     * @throws StorageOperationException if the STS credentials cannot be obtained
     */
    static OSS create(AliyunOssStorageConfiguration configuration) {
        if (!configuration.useSecurityTokenService()) {
            return new OSSClientBuilder()
                    .build(configuration.endpoint(), configuration.accessKeyId(), configuration.accessKeySecret());
        }
        AliyunTemporaryCredentials credentials = resolveTemporaryCredentials(configuration);
        return new OSSClientBuilder()
                .build(
                        configuration.endpoint(),
                        credentials.accessKeyId(),
                        credentials.accessKeySecret(),
                        credentials.securityToken());
    }

    private static AliyunTemporaryCredentials resolveTemporaryCredentials(AliyunOssStorageConfiguration configuration) {
        AliyunTemporaryCredentials cached =
                AliyunTemporaryCredentialsCache.get(configuration.temporaryCredentialsCacheKey());
        if (cached != null) {
            return cached;
        }
        AssumeRoleResponse.Credentials credentials = assumeRole(configuration).getCredentials();
        if (credentials == null
                || isBlank(credentials.getAccessKeyId())
                || isBlank(credentials.getAccessKeySecret())
                || isBlank(credentials.getSecurityToken())) {
            throw new StorageOperationException(
                    "AssumeRole returned no usable temporary credentials for role: " + configuration.roleArn());
        }
        AliyunTemporaryCredentials resolved = new AliyunTemporaryCredentials(
                credentials.getAccessKeyId(), credentials.getAccessKeySecret(), credentials.getSecurityToken());
        AliyunTemporaryCredentialsCache.put(
                configuration.temporaryCredentialsCacheKey(),
                resolved,
                credentials.getExpiration(),
                configuration.durationSeconds());
        return resolved;
    }

    private static AssumeRoleResponse assumeRole(AliyunOssStorageConfiguration configuration) {
        AssumeRoleRequest request = new AssumeRoleRequest();
        request.setAcceptFormat(FormatType.JSON);
        // eg: acs:ram::$accountID:role/$roleName
        request.setRoleArn(configuration.roleArn());
        request.setRoleSessionName(configuration.roleSessionName());
        // validity of the temporary credential in seconds; the service default applies when unset
        if (configuration.durationSeconds() > 0) {
            request.setDurationSeconds((long) configuration.durationSeconds());
        }
        // additional policy narrowing the token permissions; unset means all role permissions
        if (!isBlank(configuration.policy())) {
            request.setPolicy(configuration.policy());
        }
        try {
            DefaultProfile profile = DefaultProfile.getProfile(
                    configuration.regionId(), configuration.accessKeyId(), configuration.accessKeySecret());
            return new DefaultAcsClient(profile).getAcsResponse(request);
        } catch (ClientException e) {
            throw new StorageOperationException("Failed to obtain Aliyun STS temporary credentials via AssumeRole", e);
        } catch (RuntimeException e) {
            throw new StorageOperationException("Failed to obtain Aliyun STS temporary credentials via AssumeRole", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** Creates the OSS client on first use, so container construction stays network-free. */
    private static final class MemoizingOssClientSupplier implements Supplier<OSS> {

        private final AliyunOssStorageConfiguration configuration;
        private OSS client;

        private MemoizingOssClientSupplier(AliyunOssStorageConfiguration configuration) {
            this.configuration = configuration;
        }

        @Override
        public synchronized OSS get() {
            if (client == null) {
                client = create(configuration);
            }
            return client;
        }
    }
}
