package io.github.cocosip.polystore.fastdfs;

import io.github.cocosip.polystore.exception.StorageOperationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Local, two-column index translating polystore logical file names to FastDFS file ids
 * ({@code group/path}). FastDFS generates server-side ids and cannot address files by name, so
 * this persistent mapping backs the {@code get/exists/delete/getUrl} operations. The index file
 * is shared-writer safe within one process and survives restarts.
 */
final class LocalFileIndex {

    private final Path file;
    private final Properties entries = new Properties();

    /**
     * Opens (or creates) the index file.
     *
     * @param file index file path
     */
    LocalFileIndex(Path file) {
        this.file = file;
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                entries.load(in);
            } catch (IOException e) {
                throw new StorageOperationException("Cannot read FastDFS index: " + file, e);
            }
        }
    }

    /**
     * Records the mapping for a logical file name.
     *
     * @param fileName logical file name
     * @param fileId   FastDFS file id ({@code group/path})
     */
    synchronized void put(String fileName, String fileId) {
        entries.setProperty(fileName, fileId);
        persist();
    }

    /**
     * Returns the FastDFS file id for the logical name.
     *
     * @param fileName logical file name
     * @return file id, or {@code null} when unknown
     */
    synchronized String get(String fileName) {
        return entries.getProperty(fileName);
    }

    /**
     * Removes the mapping for the logical name; unknown names are ignored.
     *
     * @param fileName logical file name
     */
    synchronized void remove(String fileName) {
        if (entries.remove(fileName) != null) {
            persist();
        }
    }

    /**
     * Returns all indexed logical names.
     *
     * @return logical names, never {@code null}
     */
    synchronized Set<String> names() {
        return new LinkedHashSet<>(entries.stringPropertyNames());
    }

    private void persist() {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream out = Files.newOutputStream(file)) {
                entries.store(out, "polystore fastdfs index (logical name -> group/path)");
            }
        } catch (IOException e) {
            throw new StorageOperationException("Cannot write FastDFS index: " + file, e);
        }
    }
}
