package io.github.cocosip.polystore.aliyunoss;

import com.aliyun.oss.common.auth.Credentials;
import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.oss.common.auth.DefaultCredentials;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.auth.sts.AssumeRoleRequest;
import com.aliyuncs.auth.sts.AssumeRoleResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.FormatType;
import com.aliyuncs.profile.DefaultProfile;
import io.github.cocosip.polystore.exception.StorageOperationException;

/**
 * Resolves Aliyun STS temporary credentials on demand, mirroring the credential refresh of the
 * reference {@code DefaultOssClientFactory}: every OSS request asks this provider for the current
 * credentials. Valid credentials come from {@link AliyunTemporaryCredentialsCache}; an expired or
 * missing set is exchanged again through STS {@code AssumeRole}, so a long-lived OSS client keeps
 * working after the first session expires.
 */
final class AliyunOssStsCredentialsProvider implements CredentialsProvider {

    private final AliyunOssStorageConfiguration configuration;

    /** Creates the provider for one {@code aliyun-oss} container. */
    AliyunOssStsCredentialsProvider(AliyunOssStorageConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Credentials getCredentials() {
        AliyunTemporaryCredentials resolved = resolve();
        return new DefaultCredentials(resolved.accessKeyId(), resolved.accessKeySecret(), resolved.securityToken());
    }

    @Override
    public void setCredentials(Credentials credentials) {
        throw new UnsupportedOperationException("Credentials are resolved from STS, not injected");
    }

    private AliyunTemporaryCredentials resolve() {
        AliyunTemporaryCredentials cached =
                AliyunTemporaryCredentialsCache.get(configuration.temporaryCredentialsCacheKey());
        if (cached != null) {
            return cached;
        }
        AssumeRoleResponse.Credentials credentials = assumeRole().getCredentials();
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

    private AssumeRoleResponse assumeRole() {
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
            // the STS client owns an HTTP connection pool; shut it down after the one-shot call so
            // credential refreshes do not accumulate clients for the life of the process
            DefaultAcsClient acsClient = new DefaultAcsClient(profile);
            try {
                return acsClient.getAcsResponse(request);
            } finally {
                acsClient.shutdown();
            }
        } catch (ClientException e) {
            throw new StorageOperationException("Failed to obtain Aliyun STS temporary credentials via AssumeRole", e);
        } catch (RuntimeException e) {
            throw new StorageOperationException("Failed to obtain Aliyun STS temporary credentials via AssumeRole", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
