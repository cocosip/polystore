package io.github.cocosip.polystore.aws;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.StorageBackend;
import io.github.cocosip.polystore.StorageProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Storage provider of type {@code aws}: <b>Amazon Web Services S3 only</b>, backed by the AWS SDK
 * for Java v2. S3-compatible third-party stores (Ceph, KS3, MinIO, ...) use the {@code s3}
 * provider instead — the two are deliberately separate, exactly like {@code Aws} and {@code S3} in
 * the reference <i>SharpAbp.Abp.FileStoring</i> framework.
 *
 * <p>Provider parameters, named after {@code AwsFileProviderConfigurationNames}:</p>
 * <ul>
 *   <li>{@code region} (required) — AWS region, e.g. {@code us-east-1}</li>
 *   <li>{@code containerName} (required) — bucket name</li>
 *   <li>{@code accessKeyId} / {@code secretAccessKey} — static credentials; required unless one of
 *       the switchable credential modes below is enabled</li>
 *   <li>{@code useCredentials} (default {@code false}) — take credentials from {@code profileName}
 *       (with {@code profilesLocation}) or from the AWS default provider chain</li>
 *   <li>{@code useTemporaryCredentials} (default {@code false}) — obtain session credentials from
 *       STS {@code GetSessionToken}</li>
 *   <li>{@code useTemporaryFederatedCredentials} (default {@code false}) — obtain federated
 *       credentials from STS {@code GetFederationToken}; requires {@code name} and {@code policy}</li>
 *   <li>{@code profileName} / {@code profilesLocation} — AWS profile selection</li>
 *   <li>{@code durationSeconds} (default {@code 0}, service default) — temporary credential
 *       validity</li>
 *   <li>{@code name} / {@code policy} — federation token parameters</li>
 *   <li>{@code temporaryCredentialsCacheKey} (default {@code <container>/aws}) — process-wide cache
 *       key of the temporary credentials</li>
 *   <li>{@code createContainerIfNotExists} (default {@code false}) — create the bucket lazily
 *       before the first upload</li>
 *   <li>{@code urlExpiry} (default {@code 3600}, Polystore extension) — presigned URL expiry</li>
 * </ul>
 */
public class AwsStorageProvider implements StorageProvider {

    /** Provider type identifier of this backend. */
    public static final String TYPE = "aws";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public StorageBackend createBackend(ContainerConfiguration config) {
        AwsStorageConfiguration configuration = AwsStorageConfiguration.from(config);
        AwsCredentialsProvider credentials = AwsCredentialsResolver.resolve(configuration);
        Region awsRegion = Region.of(configuration.region());
        S3Client client = S3Client.builder()
                .region(awsRegion)
                .credentialsProvider(credentials)
                .build();
        S3Presigner presigner = S3Presigner.builder()
                .region(awsRegion)
                .credentialsProvider(credentials)
                .build();
        return new AwsStorageClient(
                client,
                presigner,
                configuration.containerName(),
                configuration.urlExpirySeconds(),
                configuration.createContainerIfNotExists());
    }
}
