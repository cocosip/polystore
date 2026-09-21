package io.github.cocosip.polystore.ks3;

import com.ksyun.ks3.dto.GetObjectResult;
import com.ksyun.ks3.dto.ObjectMetadata;
import com.ksyun.ks3.exception.Ks3ServiceException;
import com.ksyun.ks3.service.Ks3Client;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.cocosip.polystore.SaveArgs;
import io.github.cocosip.polystore.StorageClient;
import io.github.cocosip.polystore.UrlArgs;
import io.github.cocosip.polystore.exception.StorageFileNotFoundException;
import io.github.cocosip.polystore.exception.StorageOperationException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collection;

/**
 * Kingsoft Cloud KS3-backed {@link StorageClient}. All requests are signed with the KS3 native
 * signature by the SDK (see {@link Ks3ConnectionFactory}); {@code getUrl} returns a presigned GET
 * URL computed locally (no network round trip).
 *
 * <p>{@code save} buffers the stream in memory to provide the content length the SDK requires. When
 * {@code createContainerIfNotExists} is enabled the bucket is created lazily right before the first
 * upload, exactly like the reference provider does.</p>
 */
public final class Ks3StorageClient implements StorageClient {

    private final Ks3Client client;
    private final String bucketName;
    private final int urlExpirySeconds;
    private final boolean createContainerIfNotExists;

    /**
     * Creates the client.
     *
     * @param client                     initialized KS3 client, never {@code null}
     * @param bucketName                 target bucket, never {@code null}
     * @param urlExpirySeconds           default presigned URL expiry in seconds
     * @param createContainerIfNotExists create the bucket before the first upload when absent
     */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "wrapping the backend SDK client is the purpose of this class")
    public Ks3StorageClient(
            Ks3Client client, String bucketName, int urlExpirySeconds, boolean createContainerIfNotExists) {
        this.client = client;
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
        if (createContainerIfNotExists) {
            ensureBucket();
        }
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(content.length);
            if (args.getContentType() != null) {
                metadata.setContentType(args.getContentType());
            }
            args.getMetadata().forEach(metadata::setUserMeta);
            client.putObject(bucketName, fileName, new ByteArrayInputStream(content), metadata);
        } catch (Ks3ServiceException e) {
            throw new StorageOperationException("Failed to save file: " + fileName, e);
        }
    }

    @Override
    public InputStream get(String fileName) {
        try {
            GetObjectResult result = client.getObject(bucketName, fileName);
            return result.getObject().getObjectContent();
        } catch (Ks3ServiceException e) {
            if (isNotFound(e)) {
                throw new StorageFileNotFoundException(fileName);
            }
            throw new StorageOperationException("Failed to get file: " + fileName, e);
        }
    }

    @Override
    public void delete(String fileName) {
        try {
            client.deleteObject(bucketName, fileName);
        } catch (Ks3ServiceException e) {
            throw new StorageOperationException("Failed to delete file: " + fileName, e);
        }
    }

    @Override
    public boolean exists(String fileName) {
        try {
            return client.objectExists(bucketName, fileName);
        } catch (Ks3ServiceException e) {
            if (isNotFound(e)) {
                return false;
            }
            throw new StorageOperationException("Failed to check file: " + fileName, e);
        }
    }

    @Override
    public String getUrl(String fileName, UrlArgs args) {
        // the caller's UrlArgs overrides the container-level urlExpiry only when set explicitly;
        // the untouched default defers to the provider parameter
        int expirySeconds = UrlArgs.DEFAULT_EXPIRY.equals(args.getExpiry())
                ? urlExpirySeconds
                : (int) Math.max(1, Math.min(Integer.MAX_VALUE, args.getExpiry().toSeconds()));
        try {
            return client.generatePresignedUrl(bucketName, fileName, expirySeconds);
        } catch (Ks3ServiceException e) {
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
            if (!client.bucketExists(bucketName)) {
                client.createBucket(bucketName);
            }
        } catch (Ks3ServiceException e) {
            throw new StorageOperationException("Failed to ensure bucket: " + bucketName, e);
        }
    }

    private static boolean isNotFound(Ks3ServiceException e) {
        return e.getStatusCode() == 404
                || e.getStatueCode() == 404
                || "NoSuchKey".equals(e.getErrorCode())
                || "NoSuchBucket".equals(e.getErrorCode());
    }
}
