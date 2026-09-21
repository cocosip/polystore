package io.github.cocosip.polystore.s3;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.DefaultStorageContainer;
import io.github.cocosip.polystore.StorageContainer;
import io.github.cocosip.polystore.StorageProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Storage provider of type {@code s3}: <b>any S3-compatible object store</b> (Ceph, KS3, MinIO,
 * Cloudflare R2, ...), backed by the AWS SDK for Java v2. Amazon Web Services itself is served by
 * the dedicated {@code aws} provider — the two are deliberately separate, exactly like {@code S3}
 * and {@code Aws} in the reference <i>SharpAbp.Abp.FileStoring</i> framework. Accordingly this
 * provider always talks to the configured server URL and never falls back to an AWS endpoint.
 *
 * <p>Provider parameters, named after {@code S3FileProviderConfigurationNames}:</p>
 * <ul>
 *   <li>{@code serverUrl} (required) — service URL, e.g. {@code http://ceph.internal:7480} or
 *       {@code s3.example.com}</li>
 *   <li>{@code accessKeyId} / {@code secretAccessKey} (required) — credentials</li>
 *   <li>{@code bucketName} (required) — bucket name</li>
 *   <li>{@code forcePathStyle} (default {@code false}) — path-style addressing, required by some
 *       compatible stores</li>
 *   <li>{@code useChunkEncoding} (default {@code false}) — AWS chunked payload signing</li>
 *   <li>{@code protocol} (default {@code 1}) — {@code 1} = HTTP, {@code 2} = HTTPS; used when
 *       {@code serverUrl} carries no scheme, otherwise the scheme of the URL wins</li>
 *   <li>{@code authenticationRegion} (default {@code us-east-1}) — region used for AWS Signature
 *       Version 4</li>
 *   <li>{@code createBucketIfNotExists} (default {@code false}) — create the bucket lazily before
 *       the first upload</li>
 *   <li>{@code urlExpiry} (default {@code 3600}, Polystore extension) — presigned URL expiry in
 *       seconds</li>
 * </ul>
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
        S3StorageConfiguration configuration = S3StorageConfiguration.from(config);

        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(configuration.accessKeyId(), configuration.secretAccessKey()));
        Region region = Region.of(configuration.authenticationRegion());
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(configuration.forcePathStyle())
                .chunkedEncodingEnabled(configuration.useChunkEncoding())
                .build();

        S3ClientBuilder clientBuilder = S3Client.builder()
                .region(region)
                .credentialsProvider(credentials)
                .endpointOverride(configuration.endpoint())
                .serviceConfiguration(s3Configuration);
        S3Presigner.Builder presignerBuilder = S3Presigner.builder()
                .region(region)
                .credentialsProvider(credentials)
                .endpointOverride(configuration.endpoint())
                .serviceConfiguration(s3Configuration);

        return DefaultStorageContainer.from(
                config,
                new S3StorageClient(
                        clientBuilder.build(),
                        presignerBuilder.build(),
                        configuration.bucketName(),
                        configuration.urlExpirySeconds(),
                        configuration.createBucketIfNotExists()));
    }
}
