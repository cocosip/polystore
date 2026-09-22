package io.github.cocosip.polystore.azure;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.ParallelTransferOptions;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Azure Blob {@link StorageBackend}. */
public final class AzureBlobStorageClient implements StorageBackend {
    private final BlobContainerClient containerClient;
    private final boolean createContainerIfNotExists;

    /**
     * Creates the Azure Blob backend.
     *
     * @param containerClient            initialized blob container client, never {@code null}
     * @param createContainerIfNotExists create the container before the first upload when missing
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "the SDK client is intentionally shared")
    public AzureBlobStorageClient(BlobContainerClient containerClient, boolean createContainerIfNotExists) {
        this.containerClient = containerClient;
        this.createContainerIfNotExists = createContainerIfNotExists;
    }

    @Override
    public String save(StorageProviderSaveArgs args) {
        BlobClient blob = containerClient.getBlobClient(args.getFileId());
        if (!args.isOverrideExisting() && exists(blob, args.getFileId())) {
            throw new StorageFileAlreadyExistsException(args.getFileId());
        }
        if (createContainerIfNotExists) ensureContainer();
        ExactLengthInputStream bounded = new ExactLengthInputStream(args.getFileStream(), args.getContentLength());
        try {
            BlobParallelUploadOptions options = new BlobParallelUploadOptions(bounded, args.getContentLength());
            boolean multipart = args.getConfiguration().isEnableAutoMultiPartUpload()
                    && args.getContentLength() > args.getConfiguration().getMultiPartUploadMinFileSize();
            if (multipart) {
                ParallelTransferOptions transfer = new ParallelTransferOptions()
                        .setMaxSingleUploadSizeLong(args.getConfiguration().getMultiPartUploadMinFileSize())
                        .setBlockSizeLong(args.getConfiguration().getMultiPartUploadShardingSize());
                options.setParallelTransferOptions(transfer);
            }
            if (args.getContentType() != null) {
                options.setHeaders(new BlobHttpHeaders().setContentType(args.getContentType()));
            }
            if (!args.getMetadata().isEmpty()) options.setMetadata(args.getMetadata());
            blob.uploadWithResponse(options, null, null);
            bounded.verifyComplete();
            return args.getFileId();
        } catch (Exception e) {
            throw new StorageOperationException("Failed to save file: " + args.getFileId(), e);
        }
    }

    @Override
    public InputStream getOrNull(StorageProviderGetArgs args) {
        BlobClient blob = containerClient.getBlobClient(args.getFileId());
        if (!exists(blob, args.getFileId())) return null;
        try {
            return blob.openInputStream();
        } catch (Exception e) {
            throw new StorageOperationException("Failed to get file: " + args.getFileId(), e);
        }
    }

    /** Existence probe translated like every other operation, so SDK failures stay in the hierarchy. */
    private boolean exists(BlobClient blob, String fileId) {
        try {
            return blob.exists();
        } catch (Exception e) {
            throw new StorageOperationException("Failed to check file: " + fileId, e);
        }
    }

    @Override
    public boolean delete(StorageProviderDeleteArgs args) {
        try {
            return containerClient.getBlobClient(args.getFileId()).deleteIfExists();
        } catch (Exception e) {
            throw new StorageOperationException("Failed to delete file: " + args.getFileId(), e);
        }
    }

    @Override
    public boolean exists(StorageProviderExistsArgs args) {
        try {
            return containerClient.getBlobClient(args.getFileId()).exists();
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
        try {
            BlobClient blob = containerClient.getBlobClient(args.getFileId());
            if (args.isCheckFileExist() && !blob.exists()) return "";
            OffsetDateTime expires = OffsetDateTime.ofInstant(args.getExpires(), ZoneOffset.UTC);
            BlobServiceSasSignatureValues values =
                    new BlobServiceSasSignatureValues(expires, new BlobSasPermission().setReadPermission(true));
            return blob.getBlobUrl() + "?" + blob.generateSas(values);
        } catch (Exception e) {
            throw new StorageOperationException("Failed to build SAS URL for: " + args.getFileId(), e);
        }
    }

    private void ensureContainer() {
        try {
            if (!containerClient.exists()) containerClient.create();
        } catch (Exception e) {
            throw new StorageOperationException(
                    "Failed to create container: " + containerClient.getBlobContainerName(), e);
        }
    }
}
