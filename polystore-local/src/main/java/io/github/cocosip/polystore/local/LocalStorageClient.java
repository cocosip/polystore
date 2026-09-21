package io.github.cocosip.polystore.local;

import io.github.cocosip.polystore.SaveArgs;
import io.github.cocosip.polystore.StorageClient;
import io.github.cocosip.polystore.UrlArgs;
import io.github.cocosip.polystore.exception.StorageFileAlreadyExistsException;
import io.github.cocosip.polystore.exception.StorageFileNotFoundException;
import io.github.cocosip.polystore.exception.StorageOperationException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;

/**
 * Filesystem-backed {@link StorageClient}. File names are resolved under the configured base path;
 * parent directories are created on demand when {@code createDirectories} is enabled.
 *
 * <p>The local filesystem has no object metadata, so {@code SaveArgs.contentType} is accepted but
 * not persisted. {@code getUrl} composes {@code urlPrefix + "/" + fileName} without signing.</p>
 */
public final class LocalStorageClient implements StorageClient {

    private final Path basePath;
    private final String urlPrefix;

    /**
     * Creates the client.
     *
     * @param basePath          storage root directory, never {@code null}
     * @param urlPrefix         HTTP static-resource prefix, may be {@code null} or empty
     * @param createDirectories whether to create missing directories on save
     */
    public LocalStorageClient(Path basePath, String urlPrefix, boolean createDirectories) {
        this.basePath = basePath;
        this.urlPrefix = urlPrefix == null ? "" : urlPrefix;
        if (createDirectories) {
            try {
                Files.createDirectories(basePath);
            } catch (Exception e) {
                throw new StorageOperationException("Cannot create base directory " + basePath, e);
            }
        }
    }

    @Override
    public void save(String fileName, InputStream inputStream, SaveArgs args) {
        Path target = resolve(fileName);
        if (!args.isOverwrite() && Files.exists(target)) {
            throw new StorageFileAlreadyExistsException(fileName);
        }
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new StorageOperationException("Failed to save file: " + fileName, e);
        }
    }

    @Override
    public InputStream get(String fileName) {
        Path target = resolve(fileName);
        try {
            return Files.newInputStream(target);
        } catch (Exception e) {
            throw new StorageFileNotFoundException(fileName);
        }
    }

    @Override
    public void delete(String fileName) {
        try {
            Files.deleteIfExists(resolve(fileName));
        } catch (Exception e) {
            throw new StorageOperationException("Failed to delete file: " + fileName, e);
        }
    }

    @Override
    public boolean exists(String fileName) {
        return Files.exists(resolve(fileName));
    }

    @Override
    public String getUrl(String fileName, UrlArgs args) {
        if (urlPrefix.isEmpty()) {
            return fileName;
        }
        return urlPrefix + "/" + fileName;
    }

    @Override
    public void deleteAll(Collection<String> fileNames) {
        fileNames.forEach(this::delete);
    }

    private Path resolve(String fileName) {
        try {
            Path resolved = basePath.resolve(fileName).normalize();
            if (!resolved.startsWith(basePath.normalize())) {
                throw new StorageOperationException("File name escapes the base path: " + fileName);
            }
            return resolved;
        } catch (StorageOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageOperationException("Invalid file name: " + fileName, e);
        }
    }
}
