package io.github.cocosip.polystore.minio;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.cocosip.polystore.SaveArgs;
import io.github.cocosip.polystore.StorageClient;
import io.github.cocosip.polystore.UrlArgs;
import io.github.cocosip.polystore.exception.StorageFileNotFoundException;
import io.github.cocosip.polystore.exception.StorageOperationException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * MinIO-backed {@link StorageClient}. All operations target one bucket through a
 * {@link MinioClient}; {@code getUrl} returns a presigned GET URL computed locally (no network
 * round trip).
 *
 * <p>When {@code createBucketIfNotExists} is enabled the bucket is created lazily right before the
 * first upload, matching the reference provider.</p>
 */
public final class MinioStorageClient implements StorageClient {

    private final MinioClient client;
    private final String bucketName;
    private final int urlExpirySeconds;
    private final boolean createBucketIfNotExists;

    /**
     * Creates the client.
     *
     * @param client                    initialized MinIO client, never {@code null}
     * @param bucketName                target bucket, never {@code null}
     * @param urlExpirySeconds          presigned URL expiry in seconds
     * @param createBucketIfNotExists   create the bucket before the first upload when absent
     */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "wrapping the backend SDK client is the purpose of this class")
    public MinioStorageClient(
            MinioClient client, String bucketName, int urlExpirySeconds, boolean createBucketIfNotExists) {
        this.client = client;
        this.bucketName = bucketName;
        this.urlExpirySeconds = urlExpirySeconds;
        this.createBucketIfNotExists = createBucketIfNotExists;
    }

    @Override
    public void save(String fileName, InputStream inputStream, SaveArgs args) {
        if (createBucketIfNotExists) {
            ensureBucket();
        }
        try {
            PutObjectArgs.Builder builder = PutObjectArgs.builder().bucket(bucketName).object(fileName).stream(
                    inputStream, -1, PutObjectArgs.MIN_MULTIPART_SIZE);
            if (args.getContentType() != null) {
                builder.contentType(args.getContentType());
            }
            builder.headers(args.getMetadata());
            client.putObject(builder.build());
        } catch (Exception e) {
            throw new StorageOperationException("Failed to save file: " + fileName, e);
        }
    }

    @Override
    public InputStream get(String fileName) {
        try {
            GetObjectResponse response = client.getObject(
                    GetObjectArgs.builder().bucket(bucketName).object(fileName).build());
            // minio returns a live stream; wrap so a missing object surfaces as our exception on
            // read errors too
            return response;
        } catch (ErrorResponseException e) {
            throw new StorageFileNotFoundException(fileName);
        } catch (Exception e) {
            throw new StorageOperationException("Failed to get file: " + fileName, e);
        }
    }

    @Override
    public void delete(String fileName) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .build());
        } catch (Exception e) {
            throw new StorageOperationException("Failed to delete file: " + fileName, e);
        }
    }

    @Override
    public boolean exists(String fileName) {
        try {
            client.statObject(
                    StatObjectArgs.builder().bucket(bucketName).object(fileName).build());
            return true;
        } catch (ErrorResponseException e) {
            String code = e.errorResponse() == null ? "" : e.errorResponse().code();
            if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code)) {
                return false;
            }
            throw new StorageOperationException("Failed to check file: " + fileName, e);
        } catch (Exception e) {
            throw new StorageOperationException("Failed to check file: " + fileName, e);
        }
    }

    @Override
    public String getUrl(String fileName, UrlArgs args) {
        // the caller's UrlArgs overrides the container-level urlExpiry only when set explicitly;
        // the untouched default defers to the provider parameter
        Duration expiry = UrlArgs.DEFAULT_EXPIRY.equals(args.getExpiry())
                ? Duration.ofSeconds(urlExpirySeconds)
                : args.getExpiry();
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(io.minio.http.Method.GET)
                    .bucket(bucketName)
                    .object(fileName)
                    .expiry(Math.max(1, (int) Math.min(Integer.MAX_VALUE, expiry.toSeconds())), TimeUnit.SECONDS)
                    .build());
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
