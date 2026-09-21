package io.github.cocosip.polystore.aliyunoss;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.util.ConfigUtils;

/**
 * Immutable parameters of one {@code aliyun-oss} container, mirroring the reference
 * {@code AliyunFileProviderConfiguration} of the C# <i>SharpAbp.Abp.FileStoring.Aliyun</i>
 * framework.
 *
 * <p>{@link #from(ContainerConfiguration)} is the only place reading the raw container properties,
 * so the provider, the OSS client factory and the storage client never touch the property map.</p>
 *
 * @param endpoint                     OSS endpoint, already rewritten to the {@code -internal}
 *                                     intranet variant when {@code useInternal} is enabled
 * @param bucketName                   target bucket, never blank
 * @param regionId                     Aliyun region id, used to build the STS profile
 * @param accessKeyId                  access key id of the sub-account
 * @param accessKeySecret              access key secret of the sub-account
 * @param useSecurityTokenService      obtain temporary credentials through STS {@code AssumeRole}
 *                                     instead of using the sub-account keys directly
 * @param roleArn                      role to assume, e.g. {@code acs:ram::$accountID:role/$name}
 * @param roleSessionName              session name identifying the temporary credentials
 * @param durationSeconds              temporary credential validity in seconds, {@code 0} for the
 *                                     service default
 * @param policy                       additional policy narrowing the assumed role, may be empty
 * @param temporaryCredentialsCacheKey process-wide cache key of the temporary credentials, so
 *                                     containers sharing a key share the cached session
 * @param urlExpirySeconds             default presigned URL expiry in seconds
 * @param createContainerIfNotExists   create the bucket lazily, right before the first upload
 */
record AliyunOssStorageConfiguration(
        String endpoint,
        String bucketName,
        String regionId,
        String accessKeyId,
        String accessKeySecret,
        boolean useSecurityTokenService,
        String roleArn,
        String roleSessionName,
        int durationSeconds,
        String policy,
        String temporaryCredentialsCacheKey,
        long urlExpirySeconds,
        boolean createContainerIfNotExists) {

    /**
     * Parses and validates the {@code aliyun-oss} parameters of the given container configuration.
     *
     * <p>When {@code useInternal} is enabled the endpoint is rewritten to its {@code -internal}
     * intranet variant here, so the resulting record always carries the endpoint actually used.</p>
     *
     * @param config container configuration carrying the provider parameters, never {@code null}
     * @return parsed configuration, never {@code null}
     * @throws IllegalStateException if {@code endpoint}, {@code bucketName}, {@code accessKeyId} or
     *                               {@code accessKeySecret} is missing or blank, or if STS mode is
     *                               enabled without {@code roleArn}, {@code roleSessionName} or
     *                               {@code regionId}
     */
    static AliyunOssStorageConfiguration from(ContainerConfiguration config) {
        var properties = config.getProperties();
        String endpoint = ConfigUtils.requireString(properties, "endpoint");
        String bucketName = ConfigUtils.requireString(properties, "bucketName");
        String accessKeyId = ConfigUtils.requireString(properties, "accessKeyId");
        String accessKeySecret = ConfigUtils.requireString(properties, "accessKeySecret");
        String regionId = ConfigUtils.optString(properties, "regionId", "");
        boolean useSecurityTokenService = ConfigUtils.optBoolean(properties, "useSecurityTokenService", false);
        String roleArn = useSecurityTokenService
                ? ConfigUtils.requireString(properties, "roleArn")
                : ConfigUtils.optString(properties, "roleArn", "");
        String roleSessionName = useSecurityTokenService
                ? ConfigUtils.requireString(properties, "roleSessionName")
                : ConfigUtils.optString(properties, "roleSessionName", "");
        if (useSecurityTokenService) {
            regionId = ConfigUtils.requireString(properties, "regionId");
        }
        int durationSeconds = ConfigUtils.optInt(properties, "durationSeconds", 0);
        String policy = ConfigUtils.optString(properties, "policy", "");
        boolean createContainerIfNotExists = ConfigUtils.optBoolean(properties, "createContainerIfNotExists", false);
        String temporaryCredentialsCacheKey =
                ConfigUtils.optString(properties, "temporaryCredentialsCacheKey", config.getName() + "/aliyun");
        long urlExpiry = ConfigUtils.optLong(properties, "urlExpiry", 3600);
        boolean useInternal = ConfigUtils.optBoolean(properties, "useInternal", false);

        String resolvedEndpoint = useInternal ? endpoint.replace(".aliyuncs.com", "-internal.aliyuncs.com") : endpoint;
        return new AliyunOssStorageConfiguration(
                resolvedEndpoint,
                bucketName,
                regionId,
                accessKeyId,
                accessKeySecret,
                useSecurityTokenService,
                roleArn,
                roleSessionName,
                durationSeconds,
                policy,
                temporaryCredentialsCacheKey,
                urlExpiry,
                createContainerIfNotExists);
    }
}
