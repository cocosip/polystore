package io.github.cocosip.polystore;

import java.io.InputStream;

/** Backend operation SPI used by {@link DefaultStorageContainer}. */
public interface StorageBackend {
    /** Saves a file and returns its logical identifier. */
    String save(StorageProviderSaveArgs args);

    /** Deletes a file and reports whether it existed. */
    boolean delete(StorageProviderDeleteArgs args);

    /** Checks whether a file exists. */
    boolean exists(StorageProviderExistsArgs args);

    /** Downloads a file and reports whether it existed. */
    boolean download(StorageProviderDownloadArgs args);

    /** Opens a file, or returns {@code null} when it does not exist. */
    InputStream getOrNull(StorageProviderGetArgs args);

    /** Resolves an access URL. */
    String getAccessUrl(StorageProviderAccessArgs args);
}
