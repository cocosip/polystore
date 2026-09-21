package io.github.cocosip.polystore.fastdfs;

import com.github.tobato.fastdfs.domain.fdfs.MetaData;
import com.github.tobato.fastdfs.domain.fdfs.StorePath;
import com.github.tobato.fastdfs.service.FastFileStorageClient;
import io.github.cocosip.polystore.SaveArgs;
import io.github.cocosip.polystore.StorageClient;
import io.github.cocosip.polystore.UrlArgs;
import io.github.cocosip.polystore.exception.StorageFileNotFoundException;
import io.github.cocosip.polystore.exception.StorageOperationException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * FastDFS-backed {@link StorageClient}.
 *
 * <p>FastDFS generates server-side file ids and cannot address files by name, so {@code save}
 * uploads the content, records the {@code logical name -> group/path} mapping in a local
 * {@link LocalFileIndex} and read operations resolve through that index. {@code getUrl} composes
 * {@code urlPrefix + "/" + fileId} (typically an Nginx proxy address) without signing.</p>
 */
public final class FastDfsStorageClient implements StorageClient {

    private final FastFileStorageClient client;
    private final LocalFileIndex index;
    private final String urlPrefix;

    /**
     * Creates the client.
     *
     * @param client    assembled tobato storage client, never {@code null}
     * @param index     logical name index, never {@code null}
     * @param urlPrefix file URL prefix (e.g. the Nginx proxy address), may be empty
     */
    FastDfsStorageClient(FastFileStorageClient client, LocalFileIndex index, String urlPrefix) {
        this.client = client;
        this.index = index;
        this.urlPrefix = urlPrefix;
    }

    @Override
    public void save(String fileName, InputStream inputStream, SaveArgs args) {
        byte[] content;
        try {
            content = inputStream.readAllBytes();
        } catch (Exception e) {
            throw new StorageOperationException("Failed to read stream for: " + fileName, e);
        }
        String fileId;
        try {
            Set<MetaData> metadata = new HashSet<>();
            if (args.getContentType() != null) {
                metadata.add(new MetaData("Content-Type", args.getContentType()));
            }
            args.getMetadata().forEach((k, v) -> metadata.add(new MetaData(k, v)));
            StorePath path = client.uploadFile(
                    new ByteArrayInputStream(content), content.length, extensionOf(fileName), metadata);
            fileId = path.getGroup() + "/" + path.getPath();
        } catch (Exception e) {
            throw new StorageOperationException("Failed to upload file: " + fileName, e);
        }
        index.put(fileName, fileId);
    }

    @Override
    public InputStream get(String fileName) {
        String fileId = requireFileId(fileName);
        byte[] content;
        try {
            content = client.downloadFile(groupOf(fileId), pathOf(fileId), InputStream::readAllBytes);
        } catch (Exception e) {
            throw new StorageOperationException("Failed to download file: " + fileName, e);
        }
        if (content == null) {
            throw new StorageOperationException("Empty download for file: " + fileName);
        }
        return new ByteArrayInputStream(content);
    }

    @Override
    public void delete(String fileName) {
        String fileId = index.get(fileName);
        if (fileId == null) {
            return; // unknown logical names behave like missing files: silently ignored
        }
        try {
            client.deleteFile(fileId);
        } catch (Exception e) {
            throw new StorageOperationException("Failed to delete file: " + fileName, e);
        }
        index.remove(fileName);
    }

    @Override
    public boolean exists(String fileName) {
        return index.get(fileName) != null;
    }

    @Override
    public String getUrl(String fileName, UrlArgs args) {
        String fileId = requireFileId(fileName);
        return urlPrefix.isEmpty() ? fileId : urlPrefix + "/" + fileId;
    }

    @Override
    public void deleteAll(Collection<String> fileNames) {
        fileNames.forEach(this::delete);
    }

    private String requireFileId(String fileName) {
        String fileId = index.get(fileName);
        if (fileId == null) {
            throw new StorageFileNotFoundException(fileName);
        }
        return fileId;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        if (dot < 0 || dot < slash) {
            return "";
        }
        return fileName.substring(dot + 1);
    }

    private static String groupOf(String fileId) {
        int slash = fileId.indexOf('/');
        if (slash <= 0) {
            throw new StorageOperationException("Invalid FastDFS file id: " + fileId);
        }
        return fileId.substring(0, slash);
    }

    private static String pathOf(String fileId) {
        int slash = fileId.indexOf('/');
        if (slash <= 0 || slash == fileId.length() - 1) {
            throw new StorageOperationException("Invalid FastDFS file id: " + fileId);
        }
        return fileId.substring(slash + 1);
    }
}
