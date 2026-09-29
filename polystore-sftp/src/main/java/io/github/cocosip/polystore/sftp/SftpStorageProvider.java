package io.github.cocosip.polystore.sftp;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.StorageBackend;
import io.github.cocosip.polystore.StorageProvider;

/**
 * Storage provider of type {@code sftp}.
 *
 * <p>Parameters: {@code host} / {@code username} / {@code basePath} (required), {@code port}
 * (default 22), {@code password} or {@code privateKeyPath} (one of the two),
 * {@code urlPrefix} (default empty), {@code poolSize} (default 5), {@code knownHosts} (default
 * empty; the known-hosts file passed to JSch — set it together with
 * {@code strictHostKeyChecking: yes} for production hardening) and {@code connectTimeout}
 * (default {@code 10000} ms — bounds the SSH session and SFTP channel connect, so an unreachable
 * host cannot park a pool slot forever). All of {@code urlPrefix}, {@code poolSize},
 * {@code knownHosts} and {@code connectTimeout} are Polystore extensions.</p>
 *
 * <p>Non-override saves check existence before uploading because SFTP has no atomic create-new
 * operation: two concurrent saves of the same file id can both pass the check (last writer wins),
 * and a connection failing mid-upload can leave a partial file behind that a retry then reports as
 * already existing.</p>
 */
public class SftpStorageProvider implements StorageProvider {

    /** Creates the provider. */
    public SftpStorageProvider() {}

    /** Provider type identifier of this backend. */
    public static final String TYPE = "sftp";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public StorageBackend createBackend(ContainerConfiguration config) {
        SftpStorageConfiguration configuration = SftpStorageConfiguration.from(config);

        SftpConnectionPool pool =
                new SftpConnectionPool(SftpChannelFactory.JSCH, configuration, configuration.poolSize());
        return new SftpStorageClient(pool, configuration.basePath(), configuration.urlPrefix());
    }
}
