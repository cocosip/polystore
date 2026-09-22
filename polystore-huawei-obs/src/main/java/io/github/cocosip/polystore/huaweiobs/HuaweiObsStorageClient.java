package io.github.cocosip.polystore.huaweiobs;

import com.obs.services.ObsClient;
import com.obs.services.exception.ObsException;
import com.obs.services.model.AbortMultipartUploadRequest;
import com.obs.services.model.CompleteMultipartUploadRequest;
import com.obs.services.model.HttpMethodEnum;
import com.obs.services.model.InitiateMultipartUploadRequest;
import com.obs.services.model.ObjectMetadata;
import com.obs.services.model.ObsObject;
import com.obs.services.model.PartEtag;
import com.obs.services.model.PutObjectRequest;
import com.obs.services.model.UploadPartRequest;
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

/** Huawei Cloud OBS backend with native multipart upload support. */
public final class HuaweiObsStorageClient implements StorageBackend {
    private static final String NO_SUCH_BUCKET = "NoSuchBucket";
    private static final String NO_SUCH_KEY = "NoSuchKey";
    private static final long MAX_PARTS = 10_000;

    private final ObsClient client;
    private final String bucketName;
    private final boolean createContainerIfNotExists;

    /** Creates the OBS backend. */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "wrapping the backend SDK client is the purpose of this class")
    public HuaweiObsStorageClient(ObsClient client, String bucketName, boolean createContainerIfNotExists) {
        this.client = client;
        this.bucketName = bucketName;
        this.createContainerIfNotExists = createContainerIfNotExists;
    }

    @Override
    public String save(StorageProviderSaveArgs args) {
        if (createContainerIfNotExists) {
            ensureContainer();
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
        PutObjectRequest request = new PutObjectRequest(bucketName, args.getFileId(), stream);
        request.setMetadata(metadata(args, true));
        request.setAutoClose(false);
        try {
            client.putObject(request);
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
            InitiateMultipartUploadRequest initiate = new InitiateMultipartUploadRequest(bucketName, args.getFileId());
            initiate.setMetadata(metadata(args, false));
            uploadId = client.initiateMultipartUpload(initiate).getUploadId();

            ExactLengthInputStream total = new ExactLengthInputStream(args.getFileStream(), args.getContentLength());
            List<PartEtag> parts = new ArrayList<>((int) partCount);
            long remaining = args.getContentLength();
            for (int number = 1; remaining > 0; number++) {
                long length = Math.min(partSize, remaining);
                ExactLengthInputStream part = new ExactLengthInputStream(total, length);
                UploadPartRequest request = new UploadPartRequest(bucketName, args.getFileId(), length, part);
                request.setUploadId(uploadId);
                request.setPartNumber(number);
                request.setAutoClose(false);
                var result = client.uploadPart(request);
                part.verifyComplete();
                parts.add(new PartEtag(result.getEtag(), number));
                remaining -= length;
            }
            total.verifyComplete();
            client.completeMultipartUpload(
                    new CompleteMultipartUploadRequest(bucketName, args.getFileId(), uploadId, parts));
            return args.getFileId();
        } catch (Exception e) {
            if (uploadId != null) {
                try {
                    client.abortMultipartUpload(
                            new AbortMultipartUploadRequest(bucketName, args.getFileId(), uploadId));
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
            ObsObject object = client.getObject(bucketName, args.getFileId());
            return object.getObjectContent();
        } catch (ObsException e) {
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
            return client.doesObjectExist(bucketName, fileId);
        } catch (ObsException e) {
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
        long expirySeconds =
                Math.max(1, Duration.between(Instant.now(), args.getExpires()).getSeconds());
        try {
            return client.createSignedUrl(
                    HttpMethodEnum.GET, bucketName, args.getFileId(), null, expirySeconds, null, null);
        } catch (Exception e) {
            throw failure("Failed to sign URL for: " + args.getFileId(), e);
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
        args.getMetadata().forEach(metadata::addUserMetadata);
        return metadata;
    }

    private void ensureContainer() {
        try {
            if (!bucketExists()) {
                client.createBucket(bucketName);
            }
        } catch (StorageOperationException e) {
            throw e;
        } catch (Exception e) {
            throw failure("Failed to create bucket: " + bucketName, e);
        }
    }

    private boolean bucketExists() {
        try {
            return client.headBucket(bucketName);
        } catch (ObsException e) {
            if (e.getResponseCode() == 404 || NO_SUCH_BUCKET.equals(e.getErrorCode())) {
                return false;
            }
            throw failure("Failed to check bucket: " + bucketName, e);
        }
    }

    private static boolean isNotFound(ObsException e) {
        return e.getResponseCode() == 404
                || NO_SUCH_BUCKET.equals(e.getErrorCode())
                || NO_SUCH_KEY.equals(e.getErrorCode());
    }

    private static long partCount(long length, long partSize) {
        return length == 0 ? 0 : 1 + ((length - 1) / partSize);
    }

    private static StorageOperationException failure(String message, Exception cause) {
        return new StorageOperationException(message, cause);
    }
}
