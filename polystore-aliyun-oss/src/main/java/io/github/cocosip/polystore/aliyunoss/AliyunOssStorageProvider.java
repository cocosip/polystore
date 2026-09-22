package io.github.cocosip.polystore.aliyunoss;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.StorageBackend;
import io.github.cocosip.polystore.StorageProvider;
import java.util.Collection;
import java.util.List;

/**
 * Storage provider of type {@code aliyun-oss} (Alibaba Cloud OSS). Parameter names follow the
 * reference <i>SharpAbp.Abp.FileStoring.Aliyun</i> {@code AliyunFileProviderConfigurationNames}, so
 * they are configured in the container's {@code aliyun-oss} section, e.g.
 * {@code aliyun-oss: { endpoint: ..., bucketName: ... }}.
 *
 * <p>Provider parameters:</p>
 * <ul>
 *   <li>{@code endpoint} (required) — OSS endpoint, e.g. {@code oss-cn-hangzhou.aliyuncs.com}</li>
 *   <li>{@code bucketName} (required) — target bucket</li>
 *   <li>{@code accessKeyId} / {@code accessKeySecret} (required) — sub-account key pair; in STS mode
 *       it is exchanged for temporary credentials</li>
 *   <li>{@code regionId} (required in STS mode) — Aliyun region id used to build the STS
 *       profile</li>
 *   <li>{@code useSecurityTokenService} (default {@code false}) — obtain temporary credentials
 *       through STS {@code AssumeRole}; requires {@code roleArn} and {@code roleSessionName}</li>
 *   <li>{@code roleArn} — role to assume, e.g. {@code acs:ram::$accountID:role/$roleName}</li>
 *   <li>{@code roleSessionName} — session name identifying the temporary credentials</li>
 *   <li>{@code durationSeconds} (default {@code 0}, service default) — temporary credential
 *       validity</li>
 *   <li>{@code policy} — additional policy narrowing the assumed role</li>
 *   <li>{@code createContainerIfNotExists} (default {@code false}) — create the bucket lazily,
 *       right before the first upload, exactly like the reference provider; container construction
 *       never touches the network</li>
 *   <li>{@code temporaryCredentialsCacheKey} (default {@code <container>/aliyun}) — process-wide
 *       cache key of the STS temporary credentials</li>
 *   <li>{@code useInternal} (default {@code false}, Polystore extension) — rewrite
 *       {@code .aliyuncs.com} endpoints to the {@code -internal} intranet variant</li>
 * </ul>
 *
 * <p>The OSS client is created lazily on first use, so constructing a container performs no network
 * call — not even when STS temporary credentials are enabled; in STS mode the credentials are
 * refreshed transparently once they expire.</p>
 */
public class AliyunOssStorageProvider implements StorageProvider {

    /** Creates the provider. */
    public AliyunOssStorageProvider() {}

    /** Provider type identifier of this backend. */
    public static final String TYPE = "aliyun-oss";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public Collection<String> getAliases() {
        return List.of("Aliyun");
    }

    @Override
    public StorageBackend createBackend(ContainerConfiguration config) {
        AliyunOssStorageConfiguration configuration = AliyunOssStorageConfiguration.from(config);

        return new AliyunOssStorageClient(
                AliyunOssClientFactory.lazyClient(configuration),
                configuration.bucketName(),
                configuration.createContainerIfNotExists());
    }
}
