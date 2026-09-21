package io.github.cocosip.polystore.sftp;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.DefaultStorageContainer;
import io.github.cocosip.polystore.StorageContainer;
import io.github.cocosip.polystore.StorageProvider;

/**
 * Storage provider of type {@code sftp}.
 *
 * <p>Parameters: {@code host} / {@code username} / {@code basePath} (required), {@code port}
 * (default 22), {@code password} or {@code privateKeyPath} (one of the two),
 * {@code urlPrefix} (default empty), {@code poolSize} (default 5) and
 * {@code strictHostKeyChecking} (default {@code no}; set to {@code yes} with a known-hosts file
 * for production hardening).</p>
 */
public class SftpStorageProvider implements StorageProvider {

    /** Provider type identifier of this backend. */
    public static final String TYPE = "sftp";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public StorageContainer createContainer(ContainerConfiguration config) {
        SftpStorageConfiguration configuration = SftpStorageConfiguration.from(config);

        SftpConnectionPool pool = new SftpConnectionPool(
                SftpChannelFactory.JSCH,
                configuration.host(),
                configuration.port(),
                configuration.username(),
                configuration.password(),
                configuration.privateKeyPath(),
                configuration.strictHostKeyChecking(),
                configuration.poolSize());
        return DefaultStorageContainer.from(
                config, new SftpStorageClient(pool, configuration.basePath(), configuration.urlPrefix()));
    }
}
