package io.github.cocosip.polystore.minio;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.DefaultStorageContainer;
import io.github.cocosip.polystore.StorageContainer;
import io.github.cocosip.polystore.StorageProvider;
import io.github.cocosip.polystore.exception.StorageOperationException;
import io.github.cocosip.polystore.util.ConfigUtils;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;

/**
 * Storage provider of type {@code minio}.
 *
 * <p>Parameters: {@code endpoint} (required), {@code accessKey} / {@code secretKey} (required),
 * {@code bucketName} (required), {@code region} (default empty), {@code secure} (default
 * {@code false}, forces HTTPS when the endpoint carries no scheme), {@code urlExpiry} (default
 * 3600 seconds, presigned URL expiry) and {@code createBucketIfAbsent} (default {@code false},
 * creates the bucket at container initialization — a network call).</p>
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
        var properties = config.getProperties();
        String endpoint = ConfigUtils.requireString(properties, "endpoint");
        String accessKey = ConfigUtils.requireString(properties, "accessKey");
        String secretKey = ConfigUtils.requireString(properties, "secretKey");
        String bucketName = ConfigUtils.requireString(properties, "bucketName");
        String region = ConfigUtils.optString(properties, "region", "");
        boolean secure = ConfigUtils.optBoolean(properties, "secure", false);
        int urlExpiry = ConfigUtils.optInt(properties, "urlExpiry", 3600);
        boolean createBucketIfAbsent = ConfigUtils.optBoolean(properties, "createBucketIfAbsent", false);

        String resolvedEndpoint = endpoint;
        if (secure && !endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            resolvedEndpoint = "https://" + endpoint;
        }
        MinioClient.Builder builder =
                MinioClient.builder().endpoint(resolvedEndpoint).credentials(accessKey, secretKey);
        if (!region.isEmpty()) {
            builder.region(region);
        }
        MinioClient client = builder.build();

        if (createBucketIfAbsent) {
            ensureBucket(client, bucketName);
        }
        return DefaultStorageContainer.from(config, new MinioStorageClient(client, bucketName, urlExpiry));
    }

    private void ensureBucket(MinioClient client, String bucketName) {
        try {
            boolean exists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
        } catch (Exception e) {
            throw new StorageOperationException("Failed to ensure bucket: " + bucketName, e);
        }
    }
}
