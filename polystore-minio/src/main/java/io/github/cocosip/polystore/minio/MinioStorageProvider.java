package io.github.cocosip.polystore.minio;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.DefaultStorageContainer;
import io.github.cocosip.polystore.StorageContainer;
import io.github.cocosip.polystore.StorageProvider;
import io.minio.MinioClient;

/**
 * Storage provider of type {@code minio}, named after {@code MinioFileProviderConfigurationNames}
 * of the reference <i>SharpAbp.Abp.FileStoring</i> framework.
 *
 * <p>Provider parameters:</p>
 * <ul>
 *   <li>{@code endPoint} (required) — MinIO service address; a scheme prefix is added when absent,
 *       taken from {@code withSSL}</li>
 *   <li>{@code accessKey} / {@code secretKey} (required) — credentials</li>
 *   <li>{@code bucketName} (required) — bucket name</li>
 *   <li>{@code withSSL} (default {@code false}) — use HTTPS</li>
 *   <li>{@code createBucketIfNotExists} (default {@code false}) — create the bucket lazily before
 *       the first upload</li>
 *   <li>{@code region} (default {@code us-east-1}, Polystore extension) — signing region; setting
 *       it lets {@code getUrl} compute the presigned URL locally instead of querying the bucket
 *       location over the network</li>
 *   <li>{@code urlExpiry} (default {@code 3600}, Polystore extension) — presigned URL expiry</li>
 * </ul>
 */
public class MinioStorageProvider implements StorageProvider {

    /** Provider type identifier of this backend. */
    public static final String TYPE = "minio";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public StorageContainer createContainer(ContainerConfiguration config) {
        MinioStorageConfiguration configuration = MinioStorageConfiguration.from(config);

        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(configuration.endPoint())
                .credentials(configuration.accessKey(), configuration.secretKey())
                .region(configuration.region());
        return DefaultStorageContainer.from(
                config,
                new MinioStorageClient(
                        builder.build(),
                        configuration.bucketName(),
                        configuration.urlExpirySeconds(),
                        configuration.createBucketIfNotExists()));
    }
}
