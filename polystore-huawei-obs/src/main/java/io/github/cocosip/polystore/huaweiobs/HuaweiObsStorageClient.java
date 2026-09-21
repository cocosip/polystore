package io.github.cocosip.polystore.huaweiobs;

import com.obs.services.ObsClient;
import com.obs.services.exception.ObsException;
import com.obs.services.model.HttpMethodEnum;
import com.obs.services.model.ObjectMetadata;
import com.obs.services.model.ObsObject;
import io.github.cocosip.polystore.SaveArgs;
import io.github.cocosip.polystore.StorageClient;
import io.github.cocosip.polystore.UrlArgs;
import io.github.cocosip.polystore.exception.StorageFileNotFoundException;
import io.github.cocosip.polystore.exception.StorageOperationException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collection;

/**
 * Huawei Cloud OBS-backed {@link StorageClient}. {@code getUrl} returns a signed GET URL computed
 * locally (no network round trip).
 *
 * <p>{@code save} buffers the stream in memory to set the content length required by the SDK. When
 * {@code createContainerIfNotExists} is enabled the bucket is created lazily right before the first
 * upload, exactly like the reference provider.</p>
 */
public final class HuaweiObsStorageClient implements StorageClient {

    /** Error code reported by OBS when the requested bucket does not exist. */
    private static final String NO_SUCH_BUCKET = "NoSuchBucket";

    private final ObsClient client;
    private final String bucketName;
    private final long urlExpirySeconds;
    private final boolean createContainerIfNotExists;

    /**
     * Creates the client.
     *
     * @param client                     initialized OBS client, never {@code null}
     * @param bucketName                 target bucket, never {@code null}
     * @param urlExpirySeconds           default signed URL expiry in seconds
     * @param createContainerIfNotExists create the bucket before the first upload when it is absent
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "wrapping the backend SDK client is the purpose of this class")
    public HuaweiObsStorageClient(
            ObsClient client, String bucketName, long urlExpirySeconds, boolean createContainerIfNotExists) {
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
            ensureContainer();
        }
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength((long) content.length);
            if (args.getContentType() != null) {
                metadata.setContentType(args.getContentType());
            }
            if (!args.getMetadata().isEmpty()) {
                args.getMetadata().forEach(metadata::addUserMetadata);
            }
            client.putObject(bucketName, fileName, new ByteArrayInputStream(content), metadata);
        } catch (ObsException e) {
            throw new StorageOperationException("Failed to save file: " + fileName, e);
        }
    }

    @Override
    public InputStream get(String fileName) {
        try {
            ObsObject object = client.getObject(bucketName, fileName);
            return object.getObjectContent();
        } catch (ObsException e) {
            if ("NoSuchKey".equals(e.getErrorCode()) || e.getResponseCode() == 404) {
                throw new StorageFileNotFoundException(fileName);
            }
            throw new StorageOperationException("Failed to get file: " + fileName, e);
        }
    }

    @Override
    public void delete(String fileName) {
        try {
            client.deleteObject(bucketName, fileName);
        } catch (ObsException e) {
            throw new StorageOperationException("Failed to delete file: " + fileName, e);
        }
    }

    @Override
    public boolean exists(String fileName) {
        try {
            return client.doesObjectExist(bucketName, fileName);
        } catch (ObsException e) {
            throw new StorageOperationException("Failed to check file: " + fileName, e);
        }
    }

    @Override
    public String getUrl(String fileName, UrlArgs args) {
        long expirySeconds = UrlArgs.DEFAULT_EXPIRY.equals(args.getExpiry())
                ? urlExpirySeconds
                : args.getExpiry().toSeconds();
        try {
            return client.createSignedUrl(HttpMethodEnum.GET, bucketName, fileName, null, expirySeconds, null, null);
        } catch (ObsException e) {
            throw new StorageOperationException("Failed to sign URL for: " + fileName, e);
        }
    }

    @Override
    public void deleteAll(Collection<String> fileNames) {
        fileNames.forEach(this::delete);
    }

    /**
     * Creates the bucket when it is absent; a present bucket is left untouched.
     *
     * @throws StorageOperationException if the existence check or the creation fails
     */
    private void ensureContainer() {
        if (!bucketExists()) {
            createBucket();
        }
    }

    private boolean bucketExists() {
        try {
            return client.headBucket(bucketName);
        } catch (ObsException e) {
            if (e.getResponseCode() == 404 || NO_SUCH_BUCKET.equals(e.getErrorCode())) {
                return false;
            }
            throw new StorageOperationException("Failed to check bucket: " + bucketName, e);
        }
    }

    private void createBucket() {
        try {
            client.createBucket(bucketName);
        } catch (ObsException e) {
            throw new StorageOperationException("Failed to create bucket: " + bucketName, e);
        }
    }
}
