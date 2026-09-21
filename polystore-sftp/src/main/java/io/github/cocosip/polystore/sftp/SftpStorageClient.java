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
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;

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
        SftpChannelFactory.PooledSftpChannel leased = pool.borrow();
        try {
            return new ByteArrayInputStream(
                    leased.channel().get(resolve(args.getFileId())).readAllBytes());
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) return null;
            throw new StorageOperationException("Failed to get file: " + args.getFileId(), e);
        } catch (java.io.IOException e) {
            throw new StorageOperationException("Failed to read file: " + args.getFileId(), e);
        } finally {
            pool.release(leased);
        }
    }

    @Override
    public boolean delete(StorageProviderDeleteArgs args) {
        SftpChannelFactory.PooledSftpChannel leased = pool.borrow();
        try {
            leased.channel().rm(resolve(args.getFileId()));
            return true;
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) return false;
            throw new StorageOperationException("Failed to delete file: " + args.getFileId(), e);
        } finally {
            pool.release(leased);
        }
    }

    @Override
    public boolean exists(StorageProviderExistsArgs args) {
        SftpChannelFactory.PooledSftpChannel leased = pool.borrow();
        try {
            leased.channel().stat(resolve(args.getFileId()));
            return true;
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) return false;
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
        return urlPrefix.isEmpty() ? args.getFileId() : urlPrefix + "/" + args.getFileId();
    }

    private String resolve(String fileId) {
        return fileId.startsWith("/") ? basePath + fileId : basePath + "/" + fileId;
    }
}
