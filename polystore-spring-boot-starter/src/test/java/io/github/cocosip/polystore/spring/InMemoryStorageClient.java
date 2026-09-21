package io.github.cocosip.polystore.spring;

import io.github.cocosip.polystore.SaveArgs;
import io.github.cocosip.polystore.StorageClient;
import io.github.cocosip.polystore.UrlArgs;
import io.github.cocosip.polystore.exception.StorageFileAlreadyExistsException;
import io.github.cocosip.polystore.exception.StorageFileNotFoundException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Map-backed {@link StorageClient} for tests: keeps content in memory under the exact key. */
public final class InMemoryStorageClient implements StorageClient {

    private final Map<String, byte[]> store = new ConcurrentHashMap<>();

    /**
     * Returns the internal store for assertions.
     *
     * @return live map of stored content
     */
    public Map<String, byte[]> store() {
        return store;
    }

    @Override
    public void save(String fileName, InputStream inputStream, SaveArgs args) {
        byte[] content;
        try {
            content = inputStream.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("failed to read stream", e);
        }
        if (store.containsKey(fileName) && !args.isOverwrite()) {
            throw new StorageFileAlreadyExistsException(fileName);
        }
        store.put(fileName, content);
    }

    @Override
    public InputStream get(String fileName) {
        byte[] content = store.get(fileName);
        if (content == null) {
            throw new StorageFileNotFoundException(fileName);
        }
        return new ByteArrayInputStream(content);
    }

    @Override
    public void delete(String fileName) {
        store.remove(fileName);
    }

    @Override
    public boolean exists(String fileName) {
        return store.containsKey(fileName);
    }

    @Override
    public String getUrl(String fileName, UrlArgs args) {
        return "mem://" + fileName;
    }

    @Override
    public void deleteAll(Collection<String> fileNames) {
        fileNames.forEach(store::remove);
    }

    /**
     * Reads stored content as UTF-8 text for assertions.
     *
     * @param fileName key to read
     * @return content as text
     */
    public String text(String fileName) {
        return new String(store.get(fileName), StandardCharsets.UTF_8);
    }
}
