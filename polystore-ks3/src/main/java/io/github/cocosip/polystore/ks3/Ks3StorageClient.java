package io.github.cocosip.polystore.ks3;

import com.ksyun.ks3.dto.GetObjectResult;
import com.ksyun.ks3.dto.ObjectMetadata;
import com.ksyun.ks3.dto.PartETag;
import com.ksyun.ks3.exception.Ks3ServiceException;
import com.ksyun.ks3.service.Ks3Client;
import com.ksyun.ks3.service.request.InitiateMultipartUploadRequest;
import com.ksyun.ks3.service.request.UploadPartRequest;
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
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Kingsoft Cloud KS3 backend using the SDK's native KSS-signed operations. */
public final class Ks3StorageClient implements StorageBackend {
    private static final long MAX_PARTS = 10_000;

    private final Ks3Client client;
    private final String bucketName;
    private final boolean createContainerIfNotExists;

    /**
     * Creates the KS3 backend.
     *
     * @param client                     initialized KS3 SDK client, never {@code null}
     * @param bucketName                 target bucket name, never blank
     * @param createContainerIfNotExists create the bucket before the first upload when missing
     */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "wrapping the backend SDK client is the purpose of this class")
    public Ks3StorageClient(Ks3Client client, String bucketName, boolean createContainerIfNotExists) {
        this.client = client;
        this.bucketName = bucketName;
        this.createContainerIfNotExists = createContainerIfNotExists;
    }

    @Override
    public String save(StorageProviderSaveArgs args) {
        if (createContainerIfNotExists) {
            ensureBucket();
        }
        if (!args.isOverrideExisting() && exists(args.getFileId())) {
            throw new StorageFileAlreadyExistsException(args.getFileId());
        }
        boolean multipart = args.getConfiguration().isEnableAutoMultiPartUpload()
                && args.getContentLength() > args.getConfiguration().getMultiPartUploadMinFileSize();
        return multipart ? multipartSave(args) : singleSave(args);
    }

    private String singleSave(StorageProviderSaveArgs args) {
        ExactLengthInputStream stream = new ExactLengthInputStream(args.getFileStream(), args.getContentLength());
        try {
            client.putObject(bucketName, args.getFileId(), stream, metadata(args, true));
            stream.verifyComplete();
            return args.getFileId();
        } catch (Exception e) {
            throw failure("Failed to save file: " + args.getFileId(), e);
        }
    }

    private String multipartSave(StorageProviderSaveArgs args) {
        long partSize = args.getConfiguration().getMultiPartUploadShardingSize();
        long partCount = partCount(args.getContentLength(), partSize);
        if (partCount > MAX_PARTS) {
            throw new IllegalStateException("Multipart upload exceeds 10000 parts");
        }

        String uploadId = null;
        try {
            uploadId = client.initiateMultipartUpload(
                            new InitiateMultipartUploadRequest(bucketName, args.getFileId(), metadata(args, false)))
                    .getUploadId();
            ExactLengthInputStream total = new ExactLengthInputStream(args.getFileStream(), args.getContentLength());
            List<PartETag> parts = new ArrayList<>((int) partCount);
            long remaining = args.getContentLength();
            for (int number = 1; remaining > 0; number++) {
                long length = Math.min(partSize, remaining);
                ExactLengthInputStream part = new ExactLengthInputStream(total, length);
                PartETag result = client.uploadPart(
                        new UploadPartRequest(bucketName, args.getFileId(), uploadId, number, part, length));
                part.verifyComplete();
                parts.add(result);
                remaining -= length;
            }
            total.verifyComplete();
            client.completeMultipartUpload(bucketName, args.getFileId(), uploadId, parts);
            return args.getFileId();
        } catch (Exception e) {
            if (uploadId != null) {
                try {
                    client.abortMultipartUpload(bucketName, args.getFileId(), uploadId);
                } catch (Exception abortFailure) {
                    e.addSuppressed(abortFailure);
                }
            }
            throw failure("Failed to save file: " + args.getFileId(), e);
        }
    }

    @Override
    public InputStream getOrNull(StorageProviderGetArgs args) {
        try {
            GetObjectResult result = client.getObject(bucketName, args.getFileId());
            return result.getObject().getObjectContent();
        } catch (Ks3ServiceException e) {
            if (isNotFound(e)) {
                return null;
            }
            throw failure("Failed to get file: " + args.getFileId(), e);
        } catch (Exception e) {
            throw failure("Failed to get file: " + args.getFileId(), e);
        }
    }

    @Override
    public boolean delete(StorageProviderDeleteArgs args) {
        if (!exists(args.getFileId())) {
            return false;
        }
        try {
            client.deleteObject(bucketName, args.getFileId());
            return true;
        } catch (Exception e) {
            throw failure("Failed to delete file: " + args.getFileId(), e);
        }
    }

    @Override
    public boolean exists(StorageProviderExistsArgs args) {
        return exists(args.getFileId());
    }

    private boolean exists(String fileId) {
        try {
            return client.objectExists(bucketName, fileId);
        } catch (Ks3ServiceException e) {
            if (isNotFound(e)) {
                return false;
            }
            throw failure("Failed to check file: " + fileId, e);
        } catch (Exception e) {
            throw failure("Failed to check file: " + fileId, e);
        }
    }

    @Override
    public boolean download(StorageProviderDownloadArgs args) {
        InputStream stream = getOrNull(
                new StorageProviderGetArgs(args.getContainerName(), args.getConfiguration(), args.getFileId()));
        if (stream == null) {
            return false;
        }
        try (stream) {
            Files.copy(stream, args.getPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            throw failure("Failed to download file: " + args.getFileId(), e);
        }
    }

    @Override
    public String getAccessUrl(StorageProviderAccessArgs args) {
        if (args.isCheckFileExist() && !exists(args.getFileId())) {
            return "";
        }
        long seconds =
                Math.max(1, Duration.between(Instant.now(), args.getExpires()).getSeconds());
        int expirySeconds = (int) Math.min(Integer.MAX_VALUE, seconds);
        try {
            return client.generatePresignedUrl(bucketName, args.getFileId(), expirySeconds);
        } catch (Exception e) {
            throw failure("Failed to presign URL for: " + args.getFileId(), e);
        }
    }

    private ObjectMetadata metadata(StorageProviderSaveArgs args, boolean includeLength) {
        ObjectMetadata metadata = new ObjectMetadata();
        if (includeLength) {
            metadata.setContentLength(args.getContentLength());
        }
        if (args.getContentType() != null) {
            metadata.setContentType(args.getContentType());
        }
        args.getMetadata().forEach(metadata::setUserMeta);
        return metadata;
    }

    private void ensureBucket() {
        try {
            if (!client.bucketExists(bucketName)) {
                client.createBucket(bucketName);
            }
        } catch (Exception e) {
            throw failure("Failed to ensure bucket: " + bucketName, e);
        }
    }

    private static long partCount(long length, long partSize) {
        return length == 0 ? 0 : 1 + ((length - 1) / partSize);
    }

    private static boolean isNotFound(Ks3ServiceException e) {
        return e.getStatusCode() == 404
                || e.getStatueCode() == 404
                || "NoSuchKey".equals(e.getErrorCode())
                || "NoSuchBucket".equals(e.getErrorCode());
    }

    private static StorageOperationException failure(String message, Exception cause) {
        return new StorageOperationException(message, cause);
    }
}
