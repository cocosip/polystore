package io.github.cocosip.polystore.aliyunoss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSErrorCode;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import io.github.cocosip.polystore.SaveArgs;
import io.github.cocosip.polystore.StorageClient;
import io.github.cocosip.polystore.UrlArgs;
import io.github.cocosip.polystore.exception.StorageFileNotFoundException;
import io.github.cocosip.polystore.exception.StorageOperationException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.Date;
import java.util.function.Supplier;

/**
 * Alibaba Cloud OSS-backed {@link StorageClient}. {@code getUrl} returns a presigned GET URL
 * computed locally (no network round trip).
 *
 * <p>{@code save} buffers the stream in memory to set the content length required by the SDK. When
 * {@code createContainerIfNotExists} is enabled the bucket is created lazily right before the first
 * upload, exactly like the reference provider. The OSS client itself is created on first use, so
 * building the container performs no network call.</p>
 */
public final class AliyunOssStorageClient implements StorageClient {

    private final Supplier<OSS> clientSupplier;
    private final String bucketName;
    private final long urlExpirySeconds;
    private final boolean createContainerIfNotExists;
    private volatile OSS client;

    /**
     * Creates the client.
     *
     * @param client                     initialized OSS client, never {@code null}
     * @param bucketName                 target bucket, never {@code null}
     * @param urlExpirySeconds           default presigned URL expiry in seconds
     * @param createContainerIfNotExists create the bucket before the first upload when it is absent
     */
    public AliyunOssStorageClient(
            OSS client, String bucketName, long urlExpirySeconds, boolean createContainerIfNotExists) {
        this(() -> client, bucketName, urlExpirySeconds, createContainerIfNotExists);
    }

    /**
     * Creates the client from a supply of the OSS client, resolved on first use so container
     * construction stays network-free even when STS temporary credentials are used.
     *
     * @param clientSupplier             supplies the OSS client, never {@code null}
     * @param bucketName                 target bucket, never {@code null}
     * @param urlExpirySeconds           default presigned URL expiry in seconds
     * @param createContainerIfNotExists create the bucket before the first upload when it is absent
     */
    AliyunOssStorageClient(
            Supplier<OSS> clientSupplier,
            String bucketName,
            long urlExpirySeconds,
            boolean createContainerIfNotExists) {
        this.clientSupplier = clientSupplier;
        this.bucketName = bucketName;
        this.urlExpirySeconds = urlExpirySeconds;
        this.createContainerIfNotExists = createContainerIfNotExists;
    }

    @Override
    public void save(String fileName, InputStream inputStream, SaveArgs args) {
        byte[] content;
        try {
            content = inputStream.readAllBytes();
        } catch (Exception e) {
            throw new StorageOperationException("Failed to read stream for: " + fileName, e);
        }
        OSS ossClient = client();
        if (createContainerIfNotExists) {
            ensureContainer(ossClient);
        }
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(content.length);
            if (args.getContentType() != null) {
                metadata.setContentType(args.getContentType());
            }
            if (!args.getMetadata().isEmpty()) {
                args.getMetadata().forEach(metadata::addUserMetadata);
            }
            ossClient.putObject(
                    new PutObjectRequest(bucketName, fileName, new ByteArrayInputStream(content), metadata));
        } catch (Exception e) {
            throw new StorageOperationException("Failed to save file: " + fileName, e);
        }
    }

    @Override
    public InputStream get(String fileName) {
        try {
            OSSObject object = client().getObject(new GetObjectRequest(bucketName, fileName));
            return object.getObjectContent();
        } catch (OSSException e) {
            if (OSSErrorCode.NO_SUCH_KEY.equals(e.getErrorCode())) {
                throw new StorageFileNotFoundException(fileName);
            }
            throw new StorageOperationException("Failed to get file: " + fileName, e);
        } catch (Exception e) {
            throw new StorageOperationException("Failed to get file: " + fileName, e);
        }
    }

    @Override
    public void delete(String fileName) {
        try {
            client().deleteObject(bucketName, fileName);
        } catch (Exception e) {
            throw new StorageOperationException("Failed to delete file: " + fileName, e);
        }
    }

    @Override
    public boolean exists(String fileName) {
        try {
            return client().doesObjectExist(bucketName, fileName);
        } catch (Exception e) {
            throw new StorageOperationException("Failed to check file: " + fileName, e);
        }
    }

    @Override
    public String getUrl(String fileName, UrlArgs args) {
        long expirySeconds = UrlArgs.DEFAULT_EXPIRY.equals(args.getExpiry())
                ? urlExpirySeconds
                : args.getExpiry().toSeconds();
        try {
            Date expiration = new Date(System.currentTimeMillis() + expirySeconds * 1000);
            return client().generatePresignedUrl(bucketName, fileName, expiration)
                    .toString();
        } catch (Exception e) {
            throw new StorageOperationException("Failed to presign URL for: " + fileName, e);
        }
    }

    @Override
    public void deleteAll(Collection<String> fileNames) {
        fileNames.forEach(this::delete);
    }

    /** Resolves the OSS client once and reuses it afterwards. */
    private OSS client() {
        OSS current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    current = clientSupplier.get();
                    client = current;
                }
            }
        }
        return current;
    }

    /**
     * Creates the bucket when it is absent; a present bucket is left untouched.
     *
     * @param ossClient resolved OSS client
     * @throws StorageOperationException if the existence check or the creation fails
     */
    private void ensureContainer(OSS ossClient) {
        if (!bucketExists(ossClient)) {
            createBucket(ossClient);
        }
    }

    private boolean bucketExists(OSS ossClient) {
        try {
            return ossClient.doesBucketExist(bucketName);
        } catch (Exception e) {
            throw new StorageOperationException("Failed to check bucket: " + bucketName, e);
        }
    }

    private void createBucket(OSS ossClient) {
        try {
            ossClient.createBucket(bucketName);
        } catch (Exception e) {
            throw new StorageOperationException("Failed to create bucket: " + bucketName, e);
        }
    }
}
