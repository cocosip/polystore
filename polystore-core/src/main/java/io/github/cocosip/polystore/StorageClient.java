package io.github.cocosip.polystore;

import io.github.cocosip.polystore.exception.StorageFileNotFoundException;
import io.github.cocosip.polystore.exception.StorageOperationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/** Unified public file-storage operations API. */
public interface StorageClient {
    /** Saves content using the SharpAbp-aligned argument order. */
    String save(
            String fileId,
            InputStream stream,
            long contentLength,
            String ext,
            boolean overrideExisting,
            StorageSaveOptions options);

    /** Saves content without Polystore-specific options. */
    default String save(String fileId, InputStream stream, long contentLength, String ext, boolean overrideExisting) {
        return save(fileId, stream, contentLength, ext, overrideExisting, StorageSaveOptions.defaults());
    }

    /** Saves content without replacing an existing file. */
    default String save(String fileId, InputStream stream, long contentLength, String ext) {
        return save(fileId, stream, contentLength, ext, false);
    }

    /** Saves an in-memory byte array. */
    default String save(String fileId, byte[] bytes, String ext, boolean overrideExisting) {
        if (bytes == null) throw new IllegalArgumentException("bytes must not be null");
        try (InputStream stream = new ByteArrayInputStream(bytes)) {
            return save(fileId, stream, bytes.length, ext, overrideExisting);
        } catch (IOException e) {
            throw new StorageOperationException("Failed to close in-memory upload stream", e);
        }
    }

    /** Saves an in-memory byte array without replacing an existing file. */
    default String save(String fileId, byte[] bytes, String ext) {
        return save(fileId, bytes, ext, false);
    }

    /** Saves a file-system path. */
    default String save(String fileId, Path path, boolean overrideExisting) {
        if (path == null) throw new IllegalArgumentException("path must not be null");
        String ext = extension(path);
        try (InputStream stream = Files.newInputStream(path)) {
            return save(fileId, stream, Files.size(path), ext, overrideExisting);
        } catch (IOException e) {
            throw new StorageOperationException("Failed to save path: " + path, e);
        }
    }

    /** Saves a file-system path without replacing an existing file. */
    default String save(String fileId, Path path) {
        return save(fileId, path, false);
    }

    /** Deletes a file and reports whether it existed. */
    boolean delete(String fileId);

    /** Checks whether a file exists. */
    boolean exists(String fileId);

    /** Downloads a file and reports whether it existed. */
    boolean download(String fileId, Path path);

    /** Opens a file or returns {@code null} when missing. */
    InputStream getOrNull(String fileId);

    /** Opens a file or raises a common not-found exception. */
    default InputStream get(String fileId) {
        InputStream stream = getOrNull(fileId);
        if (stream == null) throw new StorageFileNotFoundException(fileId);
        return stream;
    }

    /** Returns all bytes and closes the backend stream. */
    default byte[] getAllBytes(String fileId) {
        try (InputStream stream = get(fileId)) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new StorageOperationException("Failed to read file: " + fileId, e);
        }
    }

    /** Returns all bytes, or {@code null} when missing, and closes the backend stream. */
    default byte[] getAllBytesOrNull(String fileId) {
        InputStream stream = getOrNull(fileId);
        if (stream == null) return null;
        try (stream) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new StorageOperationException("Failed to read file: " + fileId, e);
        }
    }

    /** Resolves an access URL. */
    String getAccessUrl(String fileId, Instant expires, boolean checkFileExist);

    /** Resolves an access URL without a preliminary existence check. */
    default String getAccessUrl(String fileId, Instant expires) {
        return getAccessUrl(fileId, expires, false);
    }

    /** Resolves an access URL expiring one hour from now. */
    default String getAccessUrl(String fileId) {
        return getAccessUrl(fileId, Instant.now().plus(Duration.ofHours(1)), false);
    }

    private static String extension(Path path) {
        Path namePath = path.getFileName();
        String name = namePath == null ? "" : namePath.toString();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            throw new IllegalArgumentException("path must have a file extension: " + path);
        }
        return name.substring(dot);
    }
}
