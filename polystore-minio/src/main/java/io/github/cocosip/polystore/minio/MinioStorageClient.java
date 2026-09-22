package io.github.cocosip.polystore.minio;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.cocosip.polystore.StorageBackend;
import io.github.cocosip.polystore.StorageProviderAccessArgs;
import io.github.cocosip.polystore.StorageProviderDeleteArgs;
import io.github.cocosip.polystore.StorageProviderDownloadArgs;
import io.github.cocosip.polystore.StorageProviderExistsArgs;
import io.github.cocosip.polystore.StorageProviderGetArgs;
import io.github.cocosip.polystore.StorageProviderSaveArgs;
import io.github.cocosip.polystore.exception.StorageFileAlreadyExistsException;
import io.github.cocosip.polystore.exception.StorageOperationException;
import io.github.cocosip.polystore.util.ExactLengthInputStream;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/** MinIO {@link StorageBackend}. */
public final class MinioStorageClient implements StorageBackend {
    private final MinioClient client;
    private final String bucketName;
    private final boolean createBucketIfNotExists;

    /** Creates the MinIO backend. */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "the SDK client is intentionally shared")
    public MinioStorageClient(MinioClient client, String bucketName, boolean createBucketIfNotExists) {
        this.client = client;
        this.bucketName = bucketName;
        this.createBucketIfNotExists = createBucketIfNotExists;
    }

    @Override
    public String save(StorageProviderSaveArgs args) {
        if (!args.isOverrideExisting()
                && exists(new StorageProviderExistsArgs(
                        args.getContainerName(), args.getConfiguration(), args.getFileId()))) {
            throw new StorageFileAlreadyExistsException(args.getFileId());
        }
        if (createBucketIfNotExists) ensureBucket();
        ExactLengthInputStream bounded = new ExactLengthInputStream(args.getFileStream(), args.getContentLength());
        boolean multipart = args.getConfiguration().isEnableAutoMultiPartUpload()
                && args.getContentLength() > args.getConfiguration().getMultiPartUploadMinFileSize();
        long partSize = multipart ? args.getConfiguration().getMultiPartUploadShardingSize() : -1;
        try {
            PutObjectArgs.Builder builder = PutObjectArgs.builder().bucket(bucketName).object(args.getFileId()).stream(
                    bounded, args.getContentLength(), partSize);
            if (args.getContentType() != null) builder.contentType(args.getContentType());
            builder.headers(args.getMetadata());
            client.putObject(builder.build());
            bounded.verifyComplete();
            return args.getFileId();
        } catch (Exception e) {
            throw new StorageOperationException("Failed to save file: " + args.getFileId(), e);
        }
    }

    @Override
    public InputStream getOrNull(StorageProviderGetArgs args) {
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(args.getFileId())
                    .build());
        } catch (ErrorResponseException e) {
            if (isNotFound(e)) return null;
            throw new StorageOperationException("Failed to get file: " + args.getFileId(), e);
        } catch (Exception e) {
            throw new StorageOperationException("Failed to get file: " + args.getFileId(), e);
        }
    }

    @Override
    public boolean delete(StorageProviderDeleteArgs args) {
        if (!exists(new StorageProviderExistsArgs(args.getContainerName(), args.getConfiguration(), args.getFileId())))
            return false;
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(args.getFileId())
                    .build());
            return true;
        } catch (Exception e) {
            throw new StorageOperationException("Failed to delete file: " + args.getFileId(), e);
        }
    }

    @Override
    public boolean exists(StorageProviderExistsArgs args) {
        try {
            client.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(args.getFileId())
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            if (isNotFound(e)) return false;
            throw new StorageOperationException("Failed to check file: " + args.getFileId(), e);
        } catch (Exception e) {
            throw new StorageOperationException("Failed to check file: " + args.getFileId(), e);
        }
    }

    @Override
    public boolean download(StorageProviderDownloadArgs args) {
        InputStream stream = getOrNull(
                new StorageProviderGetArgs(args.getContainerName(), args.getConfiguration(), args.getFileId()));
        if (stream == null) return false;
        try (stream) {
            Files.copy(stream, args.getPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            throw new StorageOperationException("Failed to download file: " + args.getFileId(), e);
        }
    }

    @Override
    public String getAccessUrl(StorageProviderAccessArgs args) {
        long seconds =
                Math.max(1, Duration.between(Instant.now(), args.getExpires()).toSeconds());
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(io.minio.http.Method.GET)
                    .bucket(bucketName)
                    .object(args.getFileId())
                    .expiry((int) Math.min(Integer.MAX_VALUE, seconds), TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            throw new StorageOperationException("Failed to presign URL for: " + args.getFileId(), e);
        }
    }

    private void ensureBucket() {
        try {
            if (!client.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
        } catch (Exception e) {
            throw new StorageOperationException("Failed to ensure bucket: " + bucketName, e);
        }
    }

    private static boolean isNotFound(ErrorResponseException e) {
        String code = e.errorResponse() == null ? "" : e.errorResponse().code();
        return "NoSuchKey".equals(code) || "NoSuchObject".equals(code) || "NoSuchBucket".equals(code);
    }
}
