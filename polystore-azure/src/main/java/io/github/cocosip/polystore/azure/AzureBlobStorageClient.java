package io.github.cocosip.polystore.azure;

import com.azure.core.http.rest.Response;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import io.github.cocosip.polystore.SaveArgs;
import io.github.cocosip.polystore.StorageClient;
import io.github.cocosip.polystore.UrlArgs;
import io.github.cocosip.polystore.exception.StorageFileNotFoundException;
import io.github.cocosip.polystore.exception.StorageOperationException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Collection;

/**
 * Azure Blob-backed {@link StorageClient}. {@code getUrl} appends a read-only SAS token with the
 * container's {@code sasExpiry} validity, unless the caller overrides {@code UrlArgs.expiry}.
 */
public final class AzureBlobStorageClient implements StorageClient {

    private final BlobContainerClient containerClient;
    private final long sasExpirySeconds;

    /**
     * Creates the client.
     *
     * @param containerClient  initialized container client, never {@code null}
     * @param sasExpirySeconds default SAS token validity in seconds
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "wrapping the backend SDK client is the purpose of this class")
    public AzureBlobStorageClient(BlobContainerClient containerClient, long sasExpirySeconds) {
        this.containerClient = containerClient;
        this.sasExpirySeconds = sasExpirySeconds;
    }

    @Override
    public void save(String fileName, InputStream inputStream, SaveArgs args) {
        try {
            BlobClient blob = containerClient.getBlobClient(fileName);
            BlobParallelUploadOptions options = new BlobParallelUploadOptions(inputStream);
            if (args.getContentType() != null) {
                options.setHeaders(
                        new com.azure.storage.blob.models.BlobHttpHeaders().setContentType(args.getContentType()));
            }
            if (!args.getMetadata().isEmpty()) {
                options.setMetadata(args.getMetadata());
            }
            Response<com.azure.storage.blob.models.BlockBlobItem> response =
                    blob.uploadWithResponse(options, null, null);
            assert response != null;
        } catch (BlobStorageException e) {
            throw new StorageOperationException("Failed to save file: " + fileName, e);
        } catch (Exception e) {
            throw new StorageOperationException("Failed to save file: " + fileName, e);
        }
    }

    @Override
    public InputStream get(String fileName) {
        BlobClient blob = containerClient.getBlobClient(fileName);
        if (!blob.exists()) {
            throw new StorageFileNotFoundException(fileName);
        }
        try {
            return blob.openInputStream();
        } catch (Exception e) {
            throw new StorageOperationException("Failed to get file: " + fileName, e);
        }
    }

    @Override
    public void delete(String fileName) {
        try {
            containerClient.getBlobClient(fileName).deleteIfExists();
        } catch (Exception e) {
            throw new StorageOperationException("Failed to delete file: " + fileName, e);
        }
    }

    @Override
    public boolean exists(String fileName) {
        try {
            return containerClient.getBlobClient(fileName).exists();
        } catch (Exception e) {
            throw new StorageOperationException("Failed to check file: " + fileName, e);
        }
    }

    @Override
    public String getUrl(String fileName, UrlArgs args) {
        long expirySeconds = UrlArgs.DEFAULT_EXPIRY.equals(args.getExpiry())
                ? sasExpirySeconds
                : args.getExpiry().toSeconds();
        try {
            BlobClient blob = containerClient.getBlobClient(fileName);
            BlobServiceSasSignatureValues values = new BlobServiceSasSignatureValues(
                    OffsetDateTime.now().plusSeconds(expirySeconds), new BlobSasPermission().setReadPermission(true));
            return blob.getBlobUrl() + "?" + blob.generateSas(values);
        } catch (Exception e) {
            throw new StorageOperationException("Failed to build SAS URL for: " + fileName, e);
        }
    }

    @Override
    public void deleteAll(Collection<String> fileNames) {
        fileNames.forEach(this::delete);
    }
}
