package io.github.cocosip.polystore.s3;

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
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/** S3-compatible {@link StorageBackend} for explicitly configured non-AWS endpoints. */
public final class S3StorageClient implements StorageBackend {
    private static final long MAX_MULTIPART_PARTS = 10_000;
    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucketName;
    private final boolean createBucketIfNotExists;

    /**
     * Creates the S3-compatible backend.
     *
     * @param client                   initialized S3 SDK client bound to the configured endpoint,
     *                                 never {@code null}
     * @param presigner                presigner bound to the same endpoint, never {@code null}
     * @param bucketName               target bucket name, never blank
     * @param createBucketIfNotExists  create the bucket before the first upload when missing
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "SDK clients are intentionally shared")
    public S3StorageClient(S3Client client, S3Presigner presigner, String bucketName, boolean createBucketIfNotExists) {
        this.client = client;
        this.presigner = presigner;
        this.bucketName = bucketName;
        this.createBucketIfNotExists = createBucketIfNotExists;
    }

    @Override
    public String save(StorageProviderSaveArgs args) {
        if (createBucketIfNotExists) ensureBucket();
        if (!args.isOverrideExisting()
                && exists(new StorageProviderExistsArgs(
                        args.getContainerName(), args.getConfiguration(), args.getFileId()))) {
            throw new StorageFileAlreadyExistsException(args.getFileId());
        }
        boolean multipart = args.getConfiguration().isEnableAutoMultiPartUpload()
                && args.getContentLength() > args.getConfiguration().getMultiPartUploadMinFileSize();
        return multipart ? multipartSave(args) : singleSave(args);
    }

    private String singleSave(StorageProviderSaveArgs args) {
        ExactLengthInputStream stream = new ExactLengthInputStream(args.getFileStream(), args.getContentLength());
        try {
            PutObjectRequest.Builder request =
                    PutObjectRequest.builder().bucket(bucketName).key(args.getFileId());
            if (args.getContentType() != null) request.contentType(args.getContentType());
            if (!args.getMetadata().isEmpty()) request.metadata(args.getMetadata());
            client.putObject(request.build(), RequestBody.fromInputStream(stream, args.getContentLength()));
            stream.verifyComplete();
            return args.getFileId();
        } catch (Exception e) {
            throw failure("Failed to save file: " + args.getFileId(), e);
        }
    }

    private String multipartSave(StorageProviderSaveArgs args) {
        long partSize = args.getConfiguration().getMultiPartUploadShardingSize();
        long partCount = partCount(args.getContentLength(), partSize);
        if (partCount > MAX_MULTIPART_PARTS) {
            throw new IllegalStateException("Multipart upload requires " + partCount + " parts, exceeding 10000");
        }
        String uploadId = null;
        try {
            CreateMultipartUploadRequest.Builder create =
                    CreateMultipartUploadRequest.builder().bucket(bucketName).key(args.getFileId());
            if (args.getContentType() != null) create.contentType(args.getContentType());
            if (!args.getMetadata().isEmpty()) create.metadata(args.getMetadata());
            uploadId = client.createMultipartUpload(create.build()).uploadId();
            ExactLengthInputStream total = new ExactLengthInputStream(args.getFileStream(), args.getContentLength());
            List<CompletedPart> completed = new ArrayList<>((int) partCount);
            long remaining = args.getContentLength();
            for (int number = 1; remaining > 0; number++) {
                long length = Math.min(partSize, remaining);
                ExactLengthInputStream part = new ExactLengthInputStream(total, length);
                String eTag = client.uploadPart(
                                UploadPartRequest.builder()
                                        .bucket(bucketName)
                                        .key(args.getFileId())
                                        .uploadId(uploadId)
                                        .partNumber(number)
                                        .contentLength(length)
                                        .build(),
                                RequestBody.fromInputStream(part, length))
                        .eTag();
                part.verifyComplete();
                completed.add(
                        CompletedPart.builder().partNumber(number).eTag(eTag).build());
                remaining -= length;
            }
            total.verifyComplete();
            client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(args.getFileId())
                    .uploadId(uploadId)
                    .multipartUpload(
                            CompletedMultipartUpload.builder().parts(completed).build())
                    .build());
            return args.getFileId();
        } catch (Exception e) {
            if (uploadId != null) abort(args.getFileId(), uploadId, e);
            throw failure("Failed to save file: " + args.getFileId(), e);
        }
    }

    @Override
    public InputStream getOrNull(StorageProviderGetArgs args) {
        try {
            return client.getObject(GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(args.getFileId())
                    .build());
        } catch (NoSuchKeyException e) {
            return null;
        } catch (AwsServiceException e) {
            if (e.statusCode() == 404) return null;
            throw new StorageOperationException("Failed to get file: " + args.getFileId(), e);
        }
    }

    @Override
    public boolean delete(StorageProviderDeleteArgs args) {
        if (!exists(new StorageProviderExistsArgs(args.getContainerName(), args.getConfiguration(), args.getFileId())))
            return false;
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(args.getFileId())
                    .build());
            return true;
        } catch (Exception e) {
            throw failure("Failed to delete file: " + args.getFileId(), e);
        }
    }

    @Override
    public boolean exists(StorageProviderExistsArgs args) {
        try {
            client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(args.getFileId())
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (AwsServiceException e) {
            if (e.statusCode() == 404) return false;
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
            throw failure("Failed to download file: " + args.getFileId(), e);
        }
    }

    @Override
    public String getAccessUrl(StorageProviderAccessArgs args) {
        if (args.isCheckFileExist()
                && !exists(new StorageProviderExistsArgs(
                        args.getContainerName(), args.getConfiguration(), args.getFileId()))) return "";
        Duration expiry = Duration.between(Instant.now(), args.getExpires());
        if (expiry.isNegative() || expiry.isZero()) expiry = Duration.ofSeconds(1);
        try {
            return presigner
                    .presignGetObject(GetObjectPresignRequest.builder()
                            .signatureDuration(expiry)
                            .getObjectRequest(GetObjectRequest.builder()
                                    .bucket(bucketName)
                                    .key(args.getFileId())
                                    .build())
                            .build())
                    .url()
                    .toString();
        } catch (Exception e) {
            throw failure("Failed to presign URL for: " + args.getFileId(), e);
        }
    }

    private void abort(String fileId, String uploadId, Exception original) {
        try {
            client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(fileId)
                    .uploadId(uploadId)
                    .build());
        } catch (Exception abortFailure) {
            original.addSuppressed(abortFailure);
        }
    }

    private void ensureBucket() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (NoSuchBucketException e) {
            createBucket();
        } catch (AwsServiceException e) {
            if (e.statusCode() != 404) throw new StorageOperationException("Failed to check bucket: " + bucketName, e);
            createBucket();
        }
    }

    private void createBucket() {
        try {
            client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        } catch (Exception e) {
            throw failure("Failed to create bucket: " + bucketName, e);
        }
    }

    private static long partCount(long length, long partSize) {
        return length == 0 ? 0 : 1 + ((length - 1) / partSize);
    }

    private static StorageOperationException failure(String message, Exception cause) {
        return cause instanceof StorageOperationException storage
                ? storage
                : new StorageOperationException(message, cause);
    }
}
