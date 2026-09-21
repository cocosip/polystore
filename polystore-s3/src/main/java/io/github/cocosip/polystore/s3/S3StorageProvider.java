package io.github.cocosip.polystore.s3;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.DefaultStorageContainer;
import io.github.cocosip.polystore.StorageContainer;
import io.github.cocosip.polystore.StorageProvider;
import io.github.cocosip.polystore.exception.StorageOperationException;
import io.github.cocosip.polystore.util.ConfigUtils;
import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Storage provider of type {@code s3} (AWS SDK for Java v2).
 *
 * <p>Parameters: {@code region} (required), {@code accessKeyId} / {@code secretAccessKey} /
 * {@code bucketName} (required), {@code endpoint} (default empty → AWS official endpoints;
 * override to target S3-compatible stores such as Ceph), {@code pathStyleAccess} (default
 * {@code false}, forces path-style addressing as required by some compatible stores) and
 * {@code urlExpiry} (default 3600 seconds, presigned URL expiry).</p>
 */
public class S3StorageProvider implements StorageProvider {

    /** Provider type identifier of this backend. */
    public static final String TYPE = "s3";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public StorageContainer createContainer(ContainerConfiguration config) {
        var properties = config.getProperties();
        String region = ConfigUtils.requireString(properties, "region");
        String accessKeyId = ConfigUtils.requireString(properties, "accessKeyId");
        String secretAccessKey = ConfigUtils.requireString(properties, "secretAccessKey");
        String bucketName = ConfigUtils.requireString(properties, "bucketName");
        String endpoint = ConfigUtils.optString(properties, "endpoint", "");
        boolean pathStyleAccess = ConfigUtils.optBoolean(properties, "pathStyleAccess", false);
        int urlExpiry = ConfigUtils.optInt(properties, "urlExpiry", 3600);

        StaticCredentialsProvider credentials =
                StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey));

        S3ClientBuilder clientBuilder =
                S3Client.builder().region(Region.of(region)).credentialsProvider(credentials);
        S3Presigner.Builder presignerBuilder =
                S3Presigner.builder().region(Region.of(region)).credentialsProvider(credentials);
        if (pathStyleAccess) {
            S3Configuration s3Configuration =
                    S3Configuration.builder().pathStyleAccessEnabled(true).build();
            clientBuilder.serviceConfiguration(s3Configuration);
            presignerBuilder.serviceConfiguration(s3Configuration);
        }
        if (!endpoint.isEmpty()) {
            try {
                URI override = URI.create(endpoint);
                clientBuilder.endpointOverride(override);
                presignerBuilder.endpointOverride(override);
            } catch (Exception e) {
                throw new StorageOperationException("Invalid endpoint: " + endpoint, e);
            }
        }

        return DefaultStorageContainer.from(
                config, new S3StorageClient(clientBuilder.build(), presignerBuilder.build(), bucketName, urlExpiry));
    }
}
