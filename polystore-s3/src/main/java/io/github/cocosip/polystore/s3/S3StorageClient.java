package io.github.cocosip.polystore.s3;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.cocosip.polystore.SaveArgs;
import io.github.cocosip.polystore.StorageClient;
import io.github.cocosip.polystore.UrlArgs;
import io.github.cocosip.polystore.exception.StorageFileNotFoundException;
import io.github.cocosip.polystore.exception.StorageOperationException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Collection;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * S3-compatible {@link StorageClient} using the AWS SDK for Java v2 against an explicitly
 * configured server URL (Ceph, KS3, MinIO, R2, ...).
 *
 * <p>{@code save} buffers the stream in memory because synchronous S3 puts require a known content
 * length. When {@code createBucketIfNotExists} is enabled the bucket is created lazily right before
 * the first upload, matching the reference provider. {@code getUrl} returns a presigned GET URL
 * computed locally (no network round trip).</p>
 */
public final class S3StorageClient implements StorageClient {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucketName;
    private final int urlExpirySeconds;
    private final boolean createBucketIfNotExists;

    /**
     * Creates the client.
     *
     * @param client                    initialized S3 client, never {@code null}
     * @param presigner                 initialized presigner matching the client configuration,
     *                                  never {@code null}
     * @param bucketName                target bucket, never {@code null}
     * @param urlExpirySeconds          default presigned URL expiry in seconds
     * @param createBucketIfNotExists   create the bucket before the first upload when absent
     */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "wrapping the backend SDK client is the purpose of this class")
    public S3StorageClient(
            S3Client client,
            S3Presigner presigner,
            String bucketName,
            int urlExpirySeconds,
            boolean createBucketIfNotExists) {
        this.client = client;
        this.presigner = presigner;
        this.bucketName = bucketName;
        this.urlExpirySeconds = urlExpirySeconds;
        this.createBucketIfNotExists = createBucketIfNotExists;
    }

    @Override
    public void save(String fileName, InputStream inputStream, SaveArgs args) {
        byte[] content;
        try {
            content = inputStream.readAllBytes();
        } catch (Exception e) {
            throw new StorageOperationException("Failed to read stream for: " + fileName, e);
        }
        if (createBucketIfNotExists) {
            ensureBucket();
        }
        try {
            PutObjectRequest.Builder builder =
                    PutObjectRequest.builder().bucket(bucketName).key(fileName);
            if (args.getContentType() != null) {
                builder.contentType(args.getContentType());
            }
            if (!args.getMetadata().isEmpty()) {
                builder.metadata(args.getMetadata());
            }
            client.putObject(builder.build(), RequestBody.fromBytes(content));
        } catch (AwsServiceException e) {
            throw new StorageOperationException("Failed to save file: " + fileName, e);
        }
    }

    @Override
    public InputStream get(String fileName) {
        try {
            return client.getObject(
                    GetObjectRequest.builder().bucket(bucketName).key(fileName).build());
        } catch (NoSuchKeyException e) {
            throw new StorageFileNotFoundException(fileName);
        } catch (AwsServiceException e) {
            throw new StorageOperationException("Failed to get file: " + fileName, e);
        }
    }

    @Override
    public void delete(String fileName) {
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build());
        } catch (AwsServiceException e) {
            throw new StorageOperationException("Failed to delete file: " + fileName, e);
        }
    }

    @Override
    public boolean exists(String fileName) {
        try {
            client.headObject(
                    HeadObjectRequest.builder().bucket(bucketName).key(fileName).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (AwsServiceException e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw new StorageOperationException("Failed to check file: " + fileName, e);
        }
    }

    @Override
    public String getUrl(String fileName, UrlArgs args) {
        Duration expiry = UrlArgs.DEFAULT_EXPIRY.equals(args.getExpiry())
                ? Duration.ofSeconds(urlExpirySeconds)
                : args.getExpiry();
        try {
            GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                    .signatureDuration(expiry)
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(fileName)
                            .build())
                    .build();
            return presigner.presignGetObject(request).url().toString();
        } catch (Exception e) {
            throw new StorageOperationException("Failed to presign URL for: " + fileName, e);
        }
    }

    @Override
    public void deleteAll(Collection<String> fileNames) {
        fileNames.forEach(this::delete);
    }

    /** Creates the bucket when it is absent; a present bucket is left untouched. */
    private void ensureBucket() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (NoSuchBucketException e) {
            createBucket();
        } catch (AwsServiceException e) {
            if (e.statusCode() != 404) {
                throw new StorageOperationException("Failed to check bucket: " + bucketName, e);
            }
            createBucket();
        }
    }

    private void createBucket() {
        try {
            client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        } catch (AwsServiceException e) {
            throw new StorageOperationException("Failed to create bucket: " + bucketName, e);
        }
    }
}
