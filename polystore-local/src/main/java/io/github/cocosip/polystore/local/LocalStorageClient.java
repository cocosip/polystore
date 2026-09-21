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
 * Filesystem-backed {@link StorageClient}. Files are stored under
 * {@code basePath/[containerName/]fileName}; the container segment is prepended only when
 * {@code appendContainerNameToBasePath} is enabled, mirroring SharpAbp's
 * {@code DefaultFilePathCalculator}.
 *
 * <p>A file name that normalizes to a path outside {@code basePath} is rejected with a
 * {@link StorageOperationException}. The local filesystem has no object metadata, so
 * {@code SaveArgs.contentType} is accepted but not persisted. {@link #getUrl(String, UrlArgs)}
 * returns the stored file's relative path, prefixed by {@code httpServer} when one is configured,
 * and never signs it.</p>
 */
public final class LocalStorageClient implements StorageClient {

    private final Path basePath;
    private final String containerName;
    private final boolean appendContainerNameToBasePath;
    private final String httpServer;

    /**
     * Creates the client.
     *
     * @param basePath                      storage root directory, never {@code null}
     * @param containerName                 container name prepended to stored paths, may be
     *                                      {@code null} or empty to disable the segment
     * @param appendContainerNameToBasePath whether stored paths are prefixed with a
     *                                      {@code containerName} segment
     * @param httpServer                    HTTP static-resource server returned by
     *                                      {@link #getUrl(String, UrlArgs)}, may be {@code null} or
     *                                      empty
     * @param createDirectories             whether to create the base directory on construction
     */
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
        String relativePath = relativePath(fileName);
        if (httpServer.isEmpty()) {
            return relativePath;
        }
        return ensureTrailingSlash(httpServer) + trimLeadingSlashes(relativePath);
    }

    @Override
    public void deleteAll(Collection<String> fileNames) {
        fileNames.forEach(this::delete);
    }

    private Path resolve(String fileName) {
        try {
            Path resolved = basePath.resolve(relativePath(fileName)).normalize();
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

    /**
     * Returns the path of a file relative to the storage root, i.e.
     * {@code [containerName/]fileName}.
     *
     * @param fileName file name as passed by the caller
     * @return relative path, never {@code null}
     */
    private String relativePath(String fileName) {
        if (appendContainerNameToBasePath && !containerName.isEmpty()) {
            return containerName + "/" + fileName;
        }
        return fileName;
    }

    private static String ensureTrailingSlash(String value) {
        return value.endsWith("/") ? value : value + "/";
    }

    private static String trimLeadingSlashes(String value) {
        int index = 0;
        while (index < value.length() && value.charAt(index) == '/') {
            index++;
        }
        return value.substring(index);
    }
}
