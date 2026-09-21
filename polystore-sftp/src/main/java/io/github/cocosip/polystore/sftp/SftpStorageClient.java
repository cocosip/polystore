package io.github.cocosip.polystore.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import io.github.cocosip.polystore.SaveArgs;
import io.github.cocosip.polystore.StorageClient;
import io.github.cocosip.polystore.UrlArgs;
import io.github.cocosip.polystore.exception.StorageFileNotFoundException;
import io.github.cocosip.polystore.exception.StorageOperationException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collection;

/**
 * SFTP-backed {@link StorageClient}. Every operation leases a channel from the
 * {@link SftpConnectionPool}; file paths resolve under the configured {@code basePath}.
 * Read operations buffer the content so the channel can be returned before the caller consumes
 * the data. {@code getUrl} composes {@code urlPrefix + "/" + fileName} without signing.
 */
public final class SftpStorageClient implements StorageClient {

    private final SftpConnectionPool pool;
    private final String basePath;
    private final String urlPrefix;

    /**
     * Creates the client.
     *
     * @param pool      connection pool, never {@code null}
     * @param basePath  remote root directory, never {@code null}
     * @param urlPrefix file URL prefix, may be empty
     */
    SftpStorageClient(SftpConnectionPool pool, String basePath, String urlPrefix) {
        this.pool = pool;
        this.basePath = basePath;
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
        String path = resolve(fileName);
        if (!args.isOverwrite() && exists(fileName)) {
            throw new io.github.cocosip.polystore.exception.StorageFileAlreadyExistsException(fileName);
        }
        SftpChannelFactory.PooledSftpChannel leased = pool.borrow();
        try {
            leased.channel().put(new ByteArrayInputStream(content), path, ChannelSftp.OVERWRITE);
        } catch (SftpException e) {
            throw new StorageOperationException("Failed to save file: " + fileName, e);
        } finally {
            pool.release(leased);
        }
    }

    @Override
    public InputStream get(String fileName) {
        String path = resolve(fileName);
        SftpChannelFactory.PooledSftpChannel leased = pool.borrow();
        try {
            byte[] content = leased.channel().get(path).readAllBytes();
            return new ByteArrayInputStream(content);
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                throw new StorageFileNotFoundException(fileName);
            }
            throw new StorageOperationException("Failed to get file: " + fileName, e);
        } catch (java.io.IOException e) {
            throw new StorageOperationException("Failed to read file content: " + fileName, e);
        } finally {
            pool.release(leased);
        }
    }

    @Override
    public void delete(String fileName) {
        String path = resolve(fileName);
        SftpChannelFactory.PooledSftpChannel leased = pool.borrow();
        try {
            leased.channel().rm(path);
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return; // missing files are silently ignored, per the StorageClient contract
            }
            throw new StorageOperationException("Failed to delete file: " + fileName, e);
        } finally {
            pool.release(leased);
        }
    }

    @Override
    public boolean exists(String fileName) {
        String path = resolve(fileName);
        SftpChannelFactory.PooledSftpChannel leased = pool.borrow();
        try {
            leased.channel().stat(path);
            return true;
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return false;
            }
            throw new StorageOperationException("Failed to check file: " + fileName, e);
        } finally {
            pool.release(leased);
        }
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

    private String resolve(String fileName) {
        if (fileName.startsWith("/")) {
            return basePath + fileName;
        }
        return basePath + "/" + fileName;
    }
}
