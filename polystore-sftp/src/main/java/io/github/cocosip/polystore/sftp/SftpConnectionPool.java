package io.github.cocosip.polystore.sftp;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Minimal fixed-size connection pool: channels are created lazily on demand and parked on return;
 * at most {@code poolSize} channels exist. Not thread-safe beyond the synchronized borrow/return
 * paths, which is sufficient for per-operation channel leases.
 */
final class SftpConnectionPool {

    private final SftpChannelFactory factory;
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String privateKeyPath;
    private final String strictHostKeyChecking;
    private final int poolSize;
    private final Deque<SftpChannelFactory.PooledSftpChannel> idle = new ArrayDeque<>();
    private int created;

    /**
     * Creates the pool.
     *
     * @param factory                channel factory (the JSch default in production)
     * @param host                   SFTP host
     * @param port                   SFTP port
     * @param username               user name
     * @param password               password, may be {@code null}
     * @param privateKeyPath         private key path, may be {@code null}
     * @param strictHostKeyChecking  JSch StrictHostKeyChecking value
     * @param poolSize               maximum number of concurrent channels
     */
    SftpConnectionPool(
            SftpChannelFactory factory,
            String host,
            int port,
            String username,
            String password,
            String privateKeyPath,
            String strictHostKeyChecking,
            int poolSize) {
        this.factory = factory;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.privateKeyPath = privateKeyPath;
        this.strictHostKeyChecking = strictHostKeyChecking;
        this.poolSize = Math.max(1, poolSize);
    }

    /**
     * Borrows a channel, creating a new one when the pool is not yet at capacity and none is
     * idle.
     *
     * @return borrowed channel, never {@code null}
     * @throws IllegalStateException if the pool is exhausted or connection fails
     */
    synchronized SftpChannelFactory.PooledSftpChannel borrow() {
        SftpChannelFactory.PooledSftpChannel channel = idle.pollFirst();
        if (channel != null) {
            return channel;
        }
        if (created >= poolSize) {
            throw new IllegalStateException("SFTP connection pool exhausted (poolSize=" + poolSize + ")");
        }
        try {
            SftpChannelFactory.PooledSftpChannel createdChannel =
                    factory.create(host, port, username, password, privateKeyPath, strictHostKeyChecking);
            created++;
            return createdChannel;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Cannot connect to SFTP " + username + "@" + host + ":" + port + ": " + e.getMessage(), e);
        }
    }

    /**
     * Returns a borrowed channel to the pool.
     *
     * @param channel previously borrowed channel
     */
    synchronized void release(SftpChannelFactory.PooledSftpChannel channel) {
        idle.addFirst(channel);
    }

    /**
     * Closes every idle channel. Borrowed channels are closed by their users.
     */
    synchronized void close() {
        while (!idle.isEmpty()) {
            idle.pollFirst().close();
        }
        created = 0;
    }
}
