package io.github.cocosip.polystore.spring;

import io.github.cocosip.polystore.StorageBackend;
import io.github.cocosip.polystore.StorageProviderAccessArgs;
import io.github.cocosip.polystore.StorageProviderDeleteArgs;
import io.github.cocosip.polystore.StorageProviderDownloadArgs;
import io.github.cocosip.polystore.StorageProviderExistsArgs;
import io.github.cocosip.polystore.StorageProviderGetArgs;
import io.github.cocosip.polystore.StorageProviderSaveArgs;
import io.github.cocosip.polystore.exception.StorageFileAlreadyExistsException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Map-backed {@link StorageBackend} used by starter tests. */
public final class InMemoryStorageClient implements StorageBackend {
    private final Map<String, byte[]> store = new ConcurrentHashMap<>();

    /** Returns the live store for assertions. */
    public Map<String, byte[]> store() {
        return store;
    }

    @Override
    public String save(StorageProviderSaveArgs args) {
        if (store.containsKey(args.getFileId()) && !args.isOverrideExisting()) {
            throw new StorageFileAlreadyExistsException(args.getFileId());
        }
        try {
            byte[] content = args.getFileStream().readNBytes(Math.toIntExact(args.getContentLength()));
            if (content.length != args.getContentLength()) throw new IllegalStateException("early EOF");
            store.put(args.getFileId(), content);
            return args.getFileId();
        } catch (Exception e) {
            throw new IllegalStateException("failed to read stream", e);
        }
    }

    @Override
    public boolean delete(StorageProviderDeleteArgs args) {
        return store.remove(args.getFileId()) != null;
    }

    @Override
    public boolean exists(StorageProviderExistsArgs args) {
        return store.containsKey(args.getFileId());
    }

    @Override
    public boolean download(StorageProviderDownloadArgs args) {
        byte[] content = store.get(args.getFileId());
        if (content == null) return false;
        try {
            Files.write(args.getPath(), content);
            return true;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public InputStream getOrNull(StorageProviderGetArgs args) {
        byte[] content = store.get(args.getFileId());
        return content == null ? null : new ByteArrayInputStream(content);
    }

    @Override
    public String getAccessUrl(StorageProviderAccessArgs args) {
        return "mem://" + args.getFileId();
    }

    /** Reads stored content as UTF-8 text. */
    public String text(String fileId) {
        return new String(store.get(fileId), java.nio.charset.StandardCharsets.UTF_8);
    }
}
