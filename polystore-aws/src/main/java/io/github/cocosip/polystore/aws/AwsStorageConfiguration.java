package io.github.cocosip.polystore.aws;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.util.ConfigUtils;

/**
 * Immutable parameters of one {@code aws} container, mirroring the reference
 * {@code AwsFileProviderConfiguration} of the C# <i>SharpAbp.Abp.FileStoring.Aws</i> framework.
 *
 * <p>Credential mode selection is part of the configuration; {@link AwsCredentialsResolver} turns it
 * into an SDK credentials provider.</p>
 *
 * @param region                            AWS region, e.g. {@code us-east-1}
 * @param containerName                     bucket name
 * @param accessKeyId                       static access key id, may be empty
 * @param secretAccessKey                   static secret access key, may be empty
 * @param useCredentials                    resolve credentials from the AWS profile / default chain
 * @param useTemporaryCredentials           obtain temporary credentials through STS
 *                                          {@code GetSessionToken}
 * @param useTemporaryFederatedCredentials  obtain federated temporary credentials through STS
 *                                          {@code GetFederationToken}
 * @param profileName                       AWS profile name, used when a profile is selected
 * @param profilesLocation                  AWS profile file or directory, may be empty
 * @param durationSeconds                   temporary credential validity in seconds, {@code 0} for
 *                                          the service default
 * @param name                              federation token name, required by the federated mode
 * @param policy                            federation token policy, required by the federated mode
 * @param temporaryCredentialsCacheKey      cache key of the temporary credentials, so containers
 *                                          sharing a key share the cached session
 * @param urlExpirySeconds                  presigned URL expiry in seconds
 * @param createContainerIfNotExists        create the bucket before the first upload when absent
 */
record AwsStorageConfiguration(
        String region,
        String containerName,
        String accessKeyId,
        String secretAccessKey,
        boolean useCredentials,
        boolean useTemporaryCredentials,
        boolean useTemporaryFederatedCredentials,
        String profileName,
        String profilesLocation,
        int durationSeconds,
        String name,
        String policy,
        String temporaryCredentialsCacheKey,
        int urlExpirySeconds,
        boolean createContainerIfNotExists) {

    /**
     * Parses the provider parameters, applying the reference defaults and validating the required
     * ones.
     *
     * @param config container configuration, never {@code null}
     * @return parsed configuration, never {@code null}
     * @throws IllegalStateException if a required parameter is missing
     */
    static AwsStorageConfiguration from(ContainerConfiguration config) {
        var properties = config.getProperties();
        return new AwsStorageConfiguration(
                ConfigUtils.requireString(properties, "region"),
                ConfigUtils.requireString(properties, "containerName"),
                ConfigUtils.optString(properties, "accessKeyId", ""),
                ConfigUtils.optString(properties, "secretAccessKey", ""),
                ConfigUtils.optBoolean(properties, "useCredentials", false),
                ConfigUtils.optBoolean(properties, "useTemporaryCredentials", false),
                ConfigUtils.optBoolean(properties, "useTemporaryFederatedCredentials", false),
                ConfigUtils.optString(properties, "profileName", ""),
                ConfigUtils.optString(properties, "profilesLocation", ""),
                ConfigUtils.optInt(properties, "durationSeconds", 0),
                ConfigUtils.optString(properties, "name", ""),
                ConfigUtils.optString(properties, "policy", ""),
                ConfigUtils.optString(properties, "temporaryCredentialsCacheKey", config.getName() + "/aws"),
                ConfigUtils.optInt(properties, "urlExpiry", 3600),
                ConfigUtils.optBoolean(properties, "createContainerIfNotExists", false));
    }
}
