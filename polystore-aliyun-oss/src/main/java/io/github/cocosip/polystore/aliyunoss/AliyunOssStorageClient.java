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

/**
 * Alibaba Cloud OSS-backed {@link StorageClient}. {@code getUrl} returns a presigned GET URL
 * computed locally (no network round trip).
 *
 * <p>{@code save} buffers the stream in memory to set the content length required by the SDK.</p>
 */
public final class AliyunOssStorageClient implements StorageClient {

    private final OSS client;
    private final String bucketName;
    private final long urlExpirySeconds;

    /**
     * Creates the client.
     *
     * @param client           initialized OSS client, never {@code null}
     * @param bucketName       target bucket, never {@code null}
     * @param urlExpirySeconds default presigned URL expiry in seconds
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "wrapping the backend SDK client is the purpose of this class")
    public AliyunOssStorageClient(OSS client, String bucketName, long urlExpirySeconds) {
        this.client = client;
        this.bucketName = bucketName;
        this.urlExpirySeconds = urlExpirySeconds;
    }

    @Override
    public void save(String fileName, InputStream inputStream, SaveArgs args) {
        byte[] content;
        try {
            content = inputStream.readAllBytes();
        } catch (Exception e) {
            throw new StorageOperationException("Failed to read stream for: " + fileName, e);
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
            client.putObject(new PutObjectRequest(bucketName, fileName, new ByteArrayInputStream(content), metadata));
        } catch (Exception e) {
            throw new StorageOperationException("Failed to save file: " + fileName, e);
        }
    }

    @Override
    public InputStream get(String fileName) {
        try {
            OSSObject object = client.getObject(new GetObjectRequest(bucketName, fileName));
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
            client.deleteObject(bucketName, fileName);
        } catch (Exception e) {
            throw new StorageOperationException("Failed to delete file: " + fileName, e);
        }
    }

    @Override
    public boolean exists(String fileName) {
        try {
            return client.doesObjectExist(bucketName, fileName);
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
            return client.generatePresignedUrl(bucketName, fileName, expiration).toString();
        } catch (Exception e) {
            throw new StorageOperationException("Failed to presign URL for: " + fileName, e);
        }
    }

    @Override
    public void deleteAll(Collection<String> fileNames) {
        fileNames.forEach(this::delete);
    }
}
