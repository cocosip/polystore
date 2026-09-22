package io.github.cocosip.polystore;

import java.io.InputStream;

/** Backend operation SPI used by {@link DefaultStorageContainer}. */
public interface StorageBackend {

    /**
     * Saves a file and returns its logical identifier.
     *
     * @param args operation arguments, never {@code null}
     * @return the logical file identifier that was stored
     */
    String save(StorageProviderSaveArgs args);

    /**
     * Deletes a file and reports whether it existed.
     *
     * @param args operation arguments, never {@code null}
     * @return {@code true} if the file existed and was removed
     */
    boolean delete(StorageProviderDeleteArgs args);

    /**
     * Checks whether a file exists.
     *
     * @param args operation arguments, never {@code null}
     * @return {@code true} if the file exists
     */
    boolean exists(StorageProviderExistsArgs args);

    /**
     * Downloads a file to a local path and reports whether it existed.
     *
     * @param args operation arguments, never {@code null}
     * @return {@code true} if the file existed and was downloaded
     */
    boolean download(StorageProviderDownloadArgs args);

    /**
     * Opens a file, or returns {@code null} when it does not exist.
     *
     * @param args operation arguments, never {@code null}
     * @return content stream, or {@code null} when the file does not exist
     */
    InputStream getOrNull(StorageProviderGetArgs args);

    /**
     * Resolves an access URL.
     *
     * @param args operation arguments, never {@code null}
     * @return the access URL, possibly empty when access is disabled
     */
    String getAccessUrl(StorageProviderAccessArgs args);
}
