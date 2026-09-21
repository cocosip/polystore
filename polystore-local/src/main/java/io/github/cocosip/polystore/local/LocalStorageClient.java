package io.github.cocosip.polystore.local;

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
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Filesystem {@link StorageBackend}. */
public final class LocalStorageClient implements StorageBackend {
    private final Path basePath;
    private final String containerName;
    private final boolean appendContainerNameToBasePath;
    private final String httpServer;

    /** Creates the filesystem backend. */
    public LocalStorageClient(
            Path basePath,
            String containerName,
            boolean appendContainerNameToBasePath,
            String httpServer,
            boolean createDirectories) {
        this.basePath = basePath;
        this.containerName = containerName == null ? "" : containerName.trim();
        this.appendContainerNameToBasePath = appendContainerNameToBasePath;
        this.httpServer = httpServer == null ? "" : httpServer.trim();
        if (createDirectories) {
            try {
                Files.createDirectories(basePath);
            } catch (Exception e) {
                throw new StorageOperationException("Cannot create base directory " + basePath, e);
            }
        }
    }

    @Override
    public String save(StorageProviderSaveArgs args) {
        Path target = resolve(args.getFileId());
        if (!args.isOverrideExisting() && Files.exists(target)) {
            throw new StorageFileAlreadyExistsException(args.getFileId());
        }
        ExactLengthInputStream bounded = new ExactLengthInputStream(args.getFileStream(), args.getContentLength());
        try {
            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.copy(bounded, target, StandardCopyOption.REPLACE_EXISTING);
            bounded.verifyComplete();
            return args.getFileId();
        } catch (Exception e) {
            throw new StorageOperationException("Failed to save file: " + args.getFileId(), e);
        }
    }

    @Override
    public InputStream getOrNull(StorageProviderGetArgs args) {
        Path target = resolve(args.getFileId());
        if (!Files.exists(target)) return null;
        try {
            return Files.newInputStream(target);
        } catch (Exception e) {
            throw new StorageOperationException("Failed to get file: " + args.getFileId(), e);
        }
    }

    @Override
    public boolean delete(StorageProviderDeleteArgs args) {
        try {
            return Files.deleteIfExists(resolve(args.getFileId()));
        } catch (Exception e) {
            throw new StorageOperationException("Failed to delete file: " + args.getFileId(), e);
        }
    }

    @Override
    public boolean exists(StorageProviderExistsArgs args) {
        return Files.exists(resolve(args.getFileId()));
    }

    @Override
    public boolean download(StorageProviderDownloadArgs args) {
        Path source = resolve(args.getFileId());
        if (!Files.exists(source)) return false;
        try {
            Files.copy(source, args.getPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            throw new StorageOperationException("Failed to download file: " + args.getFileId(), e);
        }
    }

    @Override
    public String getAccessUrl(StorageProviderAccessArgs args) {
        String relativePath = relativePath(args.getFileId());
        return httpServer.isEmpty() ? relativePath : ensureTrailingSlash(httpServer) + trimLeadingSlashes(relativePath);
    }

    private Path resolve(String fileId) {
        try {
            Path resolved = basePath.resolve(relativePath(fileId)).normalize();
            if (!resolved.startsWith(basePath.normalize())) {
                throw new StorageOperationException("File id escapes the base path: " + fileId);
            }
            return resolved;
        } catch (StorageOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageOperationException("Invalid file id: " + fileId, e);
        }
    }

    private String relativePath(String fileId) {
        return appendContainerNameToBasePath && !containerName.isEmpty() ? containerName + "/" + fileId : fileId;
    }

    private static String ensureTrailingSlash(String value) {
        return value.endsWith("/") ? value : value + "/";
    }

    private static String trimLeadingSlashes(String value) {
        int index = 0;
        while (index < value.length() && value.charAt(index) == '/') index++;
        return value.substring(index);
    }
}
