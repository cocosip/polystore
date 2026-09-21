package io.github.cocosip.polystore;

import io.github.cocosip.polystore.exception.StorageFileAlreadyExistsException;
import io.github.cocosip.polystore.exception.StorageFileNotFoundException;
import io.github.cocosip.polystore.exception.StorageOperationException;
import java.io.InputStream;
import java.util.Collection;

/**
 * Unified, storage-backend-agnostic file operations API.
 *
 * <p>Implementations are created per container by a {@link StorageProvider}; business code obtains
 * them from {@link StorageManager#getContainer(String)} and never touches backend specifics.</p>
 *
 * <p>Stream ownership: the caller stays responsible for closing the {@code inputStream} passed to
 * {@link #save(String, InputStream, SaveArgs)} and the {@code InputStream} returned by
 * {@link #get(String)}.</p>
 */
public interface StorageClient {

    /**
     * Saves a file.
     *
     * @param fileName    logical file name or path, relative to the container
     * @param inputStream file content; closed by the caller, not by this method
     * @param args        save arguments, never {@code null}
     * @throws StorageFileAlreadyExistsException if the file exists and {@link SaveArgs#isOverwrite()}
     *                                           is {@code false}
     * @throws StorageOperationException         if the backend write fails
     */
    void save(String fileName, InputStream inputStream, SaveArgs args);

    /**
     * Saves a file with {@link SaveArgs#defaults()}.
     *
     * @param fileName    logical file name or path, relative to the container
     * @param inputStream file content; closed by the caller, not by this method
     * @throws StorageFileAlreadyExistsException if the file exists and overwrite is {@code false}
     * @throws StorageOperationException         if the backend write fails
     */
    default void save(String fileName, InputStream inputStream) {
        save(fileName, inputStream, SaveArgs.defaults());
    }

    /**
     * Opens the file content.
     *
     * @param fileName logical file name or path, relative to the container
     * @return file content stream; closed by the caller, never {@code null}
     * @throws StorageFileNotFoundException if the file does not exist
     * @throws StorageOperationException    if the backend read fails
     */
    InputStream get(String fileName);

    /**
     * Deletes a file. A missing file is silently ignored.
     *
     * @param fileName logical file name or path, relative to the container
     * @throws StorageOperationException if the backend delete fails
     */
    void delete(String fileName);

    /**
     * Checks whether a file exists.
     *
     * @param fileName logical file name or path, relative to the container
     * @return {@code true} if the file exists
     * @throws StorageOperationException if the backend check fails
     */
    boolean exists(String fileName);

    /**
     * Resolves an accessible URL for the file: object storages return a presigned URL, local-style
     * backends return the static access path.
     *
     * @param fileName logical file name or path, relative to the container
     * @param args     URL arguments, never {@code null}
     * @return an accessible URL, never {@code null}
     * @throws StorageOperationException if the URL cannot be generated
     */
    String getUrl(String fileName, UrlArgs args);

    /**
     * Resolves an accessible URL with {@link UrlArgs#defaults()}.
     *
     * @param fileName logical file name or path, relative to the container
     * @return an accessible URL, never {@code null}
     * @throws StorageOperationException if the URL cannot be generated
     */
    default String getUrl(String fileName) {
        return getUrl(fileName, UrlArgs.defaults());
    }

    /**
     * Deletes multiple files. Missing files are silently ignored.
     *
     * @param fileNames logical file names or paths, relative to the container
     * @throws StorageOperationException if a backend delete fails
     */
    void deleteAll(Collection<String> fileNames);
}
