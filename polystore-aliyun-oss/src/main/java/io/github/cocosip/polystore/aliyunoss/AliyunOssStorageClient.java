package io.github.cocosip.polystore.aliyunoss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSErrorCode;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.AbortMultipartUploadRequest;
import com.aliyun.oss.model.CompleteMultipartUploadRequest;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.InitiateMultipartUploadRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PartETag;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.UploadPartRequest;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

/** Alibaba Cloud OSS {@link StorageBackend}. */
public final class AliyunOssStorageClient implements StorageBackend {
    private static final long MAX_PARTS = 10_000;
    private final Supplier<OSS> clientSupplier;
    private final String bucketName;
    private final boolean createContainerIfNotExists;
    private volatile OSS client;

    /**
     * Creates the OSS backend.
     *
     * @param client                     initialized OSS SDK client, never {@code null}
     * @param bucketName                 target bucket name, never blank
     * @param createContainerIfNotExists create the bucket before the first upload when missing
     */
    public AliyunOssStorageClient(OSS client, String bucketName, boolean createContainerIfNotExists) {
        this(() -> client, bucketName, createContainerIfNotExists);
    }

    AliyunOssStorageClient(Supplier<OSS> clientSupplier, String bucketName, boolean createContainerIfNotExists) {
        this.clientSupplier = clientSupplier;
        this.bucketName = bucketName;
        this.createContainerIfNotExists = createContainerIfNotExists;
    }

    @Override
    public String save(StorageProviderSaveArgs args) {
        OSS oss = client();
        if (createContainerIfNotExists) ensureContainer(oss);
        if (!args.isOverrideExisting() && existsOnClient(oss, args.getFileId()))
            throw new StorageFileAlreadyExistsException(args.getFileId());
        boolean multipart = args.getConfiguration().isEnableAutoMultiPartUpload()
                && args.getContentLength() > args.getConfiguration().getMultiPartUploadMinFileSize();
        return multipart ? multipartSave(oss, args) : singleSave(oss, args);
    }

    private String singleSave(OSS oss, StorageProviderSaveArgs args) {
        ExactLengthInputStream stream = new ExactLengthInputStream(args.getFileStream(), args.getContentLength());
        try {
            oss.putObject(new PutObjectRequest(bucketName, args.getFileId(), stream, metadata(args, true)));
            stream.verifyComplete();
            return args.getFileId();
        } catch (Exception e) {
            throw failure("Failed to save file: " + args.getFileId(), e);
        }
    }

    private String multipartSave(OSS oss, StorageProviderSaveArgs args) {
        long partSize = args.getConfiguration().getMultiPartUploadShardingSize();
        long count = partCount(args.getContentLength(), partSize);
        if (count > MAX_PARTS) throw new IllegalStateException("Multipart upload exceeds 10000 parts");
        String uploadId = null;
        try {
            uploadId = oss.initiateMultipartUpload(
                            new InitiateMultipartUploadRequest(bucketName, args.getFileId(), metadata(args, false)))
                    .getUploadId();
            ExactLengthInputStream total = new ExactLengthInputStream(args.getFileStream(), args.getContentLength());
            List<PartETag> parts = new ArrayList<>((int) count);
            long remaining = args.getContentLength();
            for (int number = 1; remaining > 0; number++) {
                long length = Math.min(partSize, remaining);
                ExactLengthInputStream part = new ExactLengthInputStream(total, length);
                var result = oss.uploadPart(
                        new UploadPartRequest(bucketName, args.getFileId(), uploadId, number, part, length));
                part.verifyComplete();
                parts.add(result.getPartETag());
                remaining -= length;
            }
            total.verifyComplete();
            oss.completeMultipartUpload(
                    new CompleteMultipartUploadRequest(bucketName, args.getFileId(), uploadId, parts));
            return args.getFileId();
        } catch (Exception e) {
            if (uploadId != null) {
                try {
                    oss.abortMultipartUpload(new AbortMultipartUploadRequest(bucketName, args.getFileId(), uploadId));
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
            OSSObject object = client().getObject(new GetObjectRequest(bucketName, args.getFileId()));
            return object.getObjectContent();
        } catch (OSSException e) {
            if (OSSErrorCode.NO_SUCH_KEY.equals(e.getErrorCode())) return null;
            throw failure("Failed to get file: " + args.getFileId(), e);
        } catch (Exception e) {
            throw failure("Failed to get file: " + args.getFileId(), e);
        }
    }

    @Override
    public boolean delete(StorageProviderDeleteArgs args) {
        if (!existsOnClient(client(), args.getFileId())) return false;
        try {
            client().deleteObject(bucketName, args.getFileId());
            return true;
        } catch (Exception e) {
            throw failure("Failed to delete file: " + args.getFileId(), e);
        }
    }

    @Override
    public boolean exists(StorageProviderExistsArgs args) {
        try {
            return client().doesObjectExist(bucketName, args.getFileId());
        } catch (Exception e) {
            throw failure("Failed to check file: " + args.getFileId(), e);
        }
    }

    /**
     * Existence probe on a specific client instance, translated like every other operation so SDK
     * failures never escape the {@code PolystoreException} hierarchy.
     */
    private boolean existsOnClient(OSS oss, String fileId) {
        try {
            return oss.doesObjectExist(bucketName, fileId);
        } catch (Exception e) {
            throw failure("Failed to check file: " + fileId, e);
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
        if (args.isCheckFileExist() && !client().doesObjectExist(bucketName, args.getFileId())) return "";
        try {
            return client().generatePresignedUrl(bucketName, args.getFileId(), Date.from(args.getExpires()))
                    .toString();
        } catch (Exception e) {
            throw failure("Failed to presign URL for: " + args.getFileId(), e);
        }
    }

    private ObjectMetadata metadata(StorageProviderSaveArgs args, boolean length) {
        ObjectMetadata metadata = new ObjectMetadata();
        if (length) metadata.setContentLength(args.getContentLength());
        if (args.getContentType() != null) metadata.setContentType(args.getContentType());
        args.getMetadata().forEach(metadata::addUserMetadata);
        return metadata;
    }

    private OSS client() {
        OSS current = client;
        if (current == null)
            synchronized (this) {
                if ((current = client) == null) client = current = clientSupplier.get();
            }
        return current;
    }

    private void ensureContainer(OSS oss) {
        try {
            if (!oss.doesBucketExist(bucketName)) oss.createBucket(bucketName);
        } catch (Exception e) {
            throw failure("Failed to ensure bucket: " + bucketName, e);
        }
    }

    private static long partCount(long length, long size) {
        return length == 0 ? 0 : 1 + ((length - 1) / size);
    }

    private static StorageOperationException failure(String message, Exception cause) {
        return new StorageOperationException(message, cause);
    }
}
