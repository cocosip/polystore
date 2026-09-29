package io.github.cocosip.polystore.sftp;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.util.ConfigUtils;

/**
 * Immutable parameters of one {@code sftp} container.
 *
 * @param host                  SFTP host, required
 * @param port                  SFTP port, default {@code 22}
 * @param username              user name, required
 * @param password              password, may be empty
 * @param privateKeyPath        private key path, may be empty
 * @param basePath              remote root directory, required
 * @param urlPrefix             file URL prefix, default empty
 * @param poolSize              maximum number of concurrent channels, default {@code 5}
 * @param strictHostKeyChecking JSch StrictHostKeyChecking value, default {@code no}
 * @param knownHosts            path of the known-hosts file passed to JSch, empty for the
 *                              {@code ~/.ssh/known_hosts} default; required in practice when
 *                              {@code strictHostKeyChecking} is {@code yes}
 * @param connectTimeoutMillis  connection timeout in milliseconds for the SSH session and the
 *                              SFTP channel, default {@code 10_000}
 */
record SftpStorageConfiguration(
        String host,
        int port,
        String username,
        String password,
        String privateKeyPath,
        String basePath,
        String urlPrefix,
        int poolSize,
        String strictHostKeyChecking,
        String knownHosts,
        int connectTimeoutMillis) {

    /** Default connection timeout in milliseconds for the SSH session and the SFTP channel. */
    static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 10_000;

    /**
     * Parses and validates the parameters of one {@code sftp} container.
     *
     * @param config container configuration holding the provider parameters
     * @return parsed configuration, never {@code null}
     * @throws IllegalStateException if a required parameter is missing, neither {@code password}
     *                               nor {@code privateKeyPath} is configured, or a numeric
     *                               parameter is out of range
     */
    static SftpStorageConfiguration from(ContainerConfiguration config) {
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
        String knownHosts = ConfigUtils.optString(properties, "knownHosts", "");
        int connectTimeoutMillis = ConfigUtils.optInt(properties, "connectTimeout", DEFAULT_CONNECT_TIMEOUT_MILLIS);
        if (password.isEmpty() && privateKeyPath.isEmpty()) {
            throw new IllegalStateException("SFTP credentials require either 'password' or 'privateKeyPath'");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalStateException("Invalid storage parameter 'port': " + port);
        }
        if (poolSize < 1) {
            throw new IllegalStateException("Invalid storage parameter 'poolSize': " + poolSize);
        }
        if (connectTimeoutMillis < 1) {
            throw new IllegalStateException("Invalid storage parameter 'connectTimeout': " + connectTimeoutMillis);
        }
        return new SftpStorageConfiguration(
                host,
                port,
                username,
                password,
                privateKeyPath,
                basePath,
                urlPrefix,
                poolSize,
                strictHostKeyChecking,
                knownHosts,
                connectTimeoutMillis);
    }
}
