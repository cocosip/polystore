package io.github.cocosip.polystore.sftp;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.DefaultStorageContainer;
import io.github.cocosip.polystore.StorageContainer;
import io.github.cocosip.polystore.StorageProvider;
import io.github.cocosip.polystore.util.ConfigUtils;

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
        var properties = config.getProperties();
        String host = ConfigUtils.requireString(properties, "host");
        int port = ConfigUtils.optInt(properties, "port", 22);
        String username = ConfigUtils.requireString(properties, "username");
        String password = ConfigUtils.optString(properties, "password", "");
        String privateKeyPath = ConfigUtils.optString(properties, "privateKeyPath", "");
        String basePath = ConfigUtils.requireString(properties, "basePath");
        String urlPrefix = ConfigUtils.optString(properties, "urlPrefix", "");
        int poolSize = ConfigUtils.optInt(properties, "poolSize", 5);
        String strictHostKeyChecking = ConfigUtils.optString(properties, "strictHostKeyChecking", "no");

        if (password.isEmpty() && privateKeyPath.isEmpty()) {
            throw new IllegalStateException("SFTP credentials require either 'password' or 'privateKeyPath'");
        }

        SftpConnectionPool pool = new SftpConnectionPool(
                SftpChannelFactory.JSCH,
                host,
                port,
                username,
                password,
                privateKeyPath,
                strictHostKeyChecking,
                poolSize);
        return DefaultStorageContainer.from(config, new SftpStorageClient(pool, basePath, urlPrefix));
    }
}
