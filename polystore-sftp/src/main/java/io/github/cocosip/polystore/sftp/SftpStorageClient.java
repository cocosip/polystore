package io.github.cocosip.polystore.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import io.github.cocosip.polystore.StorageBackend;
import io.github.cocosip.polystore.StorageProviderAccessArgs;
import io.github.cocosip.polystore.StorageProviderDeleteArgs;
import io.github.cocosip.polystore.StorageProviderDownloadArgs;
import io.github.cocosip.polystore.StorageProviderExistsArgs;
import io.github.cocosip.polystore.StorageProviderGetArgs;
import io.github.cocosip.polystore.StorageProviderSaveArgs;
import io.github.cocosip.polystore.exception.StorageFileAlreadyExistsException;
import io.github.cocosip.polystore.exception.StorageOperationException;
import io.github.cocosip.polystore.util.ExactLengthInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

/** SFTP {@link StorageBackend}. */
public final class SftpStorageClient implements StorageBackend {
    private final SftpConnectionPool pool;
    private final String basePath;
    private final String urlPrefix;

    /** Creates the SFTP backend. */
    SftpStorageClient(SftpConnectionPool pool, String basePath, String urlPrefix) {
        this.pool = pool;
        this.basePath = basePath;
        this.urlPrefix = urlPrefix;
    }

    @Override
    public String save(StorageProviderSaveArgs args) {
        if (!args.isOverrideExisting()
                && exists(new StorageProviderExistsArgs(
                        args.getContainerName(), args.getConfiguration(), args.getFileId()))) {
            throw new StorageFileAlreadyExistsException(args.getFileId());
        }
        validateFileId(args.getFileId());
        ExactLengthInputStream bounded = new ExactLengthInputStream(args.getFileStream(), args.getContentLength());
        SftpChannelFactory.PooledSftpChannel leased = pool.borrow();
        try {
            leased.channel().put(bounded, resolve(args.getFileId()), ChannelSftp.OVERWRITE);
            bounded.verifyComplete();
            return args.getFileId();
        } catch (Exception e) {
            throw new StorageOperationException("Failed to save file: " + args.getFileId(), e);
        } finally {
            pool.release(leased);
        }
    }

    @Override
    public InputStream getOrNull(StorageProviderGetArgs args) {
        validateFileId(args.getFileId());
        SftpChannelFactory.PooledSftpChannel leased = pool.borrow();
        InputStream remote;
        try {
            remote = leased.channel().get(resolve(args.getFileId()));
        } catch (SftpException e) {
            pool.release(leased);
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) return null;
            throw new StorageOperationException("Failed to get file: " + args.getFileId(), e);
        } catch (Exception e) {
            pool.release(leased);
            throw new StorageOperationException("Failed to get file: " + args.getFileId(), e);
        }
        // the channel stays leased until the caller closes the stream, so the remote content is
        // streamed instead of being buffered in memory
        return new LeaseHoldingInputStream(remote, pool, leased);
    }

    @Override
    public boolean delete(StorageProviderDeleteArgs args) {
        validateFileId(args.getFileId());
        SftpChannelFactory.PooledSftpChannel leased = pool.borrow();
        try {
            leased.channel().rm(resolve(args.getFileId()));
            return true;
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) return false;
            throw new StorageOperationException("Failed to delete file: " + args.getFileId(), e);
        } catch (Exception e) {
            throw new StorageOperationException("Failed to delete file: " + args.getFileId(), e);
        } finally {
            pool.release(leased);
        }
    }

    @Override
    public boolean exists(StorageProviderExistsArgs args) {
        validateFileId(args.getFileId());
        SftpChannelFactory.PooledSftpChannel leased = pool.borrow();
        try {
            leased.channel().stat(resolve(args.getFileId()));
            return true;
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) return false;
            throw new StorageOperationException("Failed to check file: " + args.getFileId(), e);
        } catch (Exception e) {
            throw new StorageOperationException("Failed to check file: " + args.getFileId(), e);
        } finally {
            pool.release(leased);
        }
    }

    @Override
    public boolean download(StorageProviderDownloadArgs args) {
        InputStream stream = getOrNull(
                new StorageProviderGetArgs(args.getContainerName(), args.getConfiguration(), args.getFileId()));
        if (stream == null) return false;
        try (stream) {
            Files.copy(stream, args.getPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            throw new StorageOperationException("Failed to download file: " + args.getFileId(), e);
        }
    }

    @Override
    public String getAccessUrl(StorageProviderAccessArgs args) {
        if (urlPrefix.isEmpty()) return args.getFileId();
        String prefix = urlPrefix.endsWith("/") ? urlPrefix.substring(0, urlPrefix.length() - 1) : urlPrefix;
        return prefix + "/" + args.getFileId();
    }

    private void validateFileId(String fileId) {
        for (String segment : fileId.split("/")) {
            if ("..".equals(segment)) {
                throw new StorageOperationException("File id escapes the base path: " + fileId);
            }
        }
    }

    private String resolve(String fileId) {
        return fileId.startsWith("/") ? basePath + fileId : basePath + "/" + fileId;
    }

    /**
     * Remote stream that holds the pooled channel lease until the caller closes it; closing
     * releases the channel back to the pool (a dead channel is evicted there). The pool is never
     * blocked while the caller consumes the stream. Closing twice is safe: only the first close
     * releases the lease, keeping the pool's idle count and size slots consistent.
     */
    private static final class LeaseHoldingInputStream extends FilterInputStream {

        private final SftpConnectionPool pool;
        private final SftpChannelFactory.PooledSftpChannel leased;
        private final AtomicBoolean released = new AtomicBoolean();

        LeaseHoldingInputStream(
                InputStream delegate, SftpConnectionPool pool, SftpChannelFactory.PooledSftpChannel leased) {
            super(delegate);
            this.pool = pool;
            this.leased = leased;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                if (released.compareAndSet(false, true)) {
                    pool.release(leased);
                }
            }
        }
    }
}
