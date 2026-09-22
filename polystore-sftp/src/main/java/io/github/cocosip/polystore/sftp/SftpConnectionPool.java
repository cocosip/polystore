package io.github.cocosip.polystore.sftp;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Fixed-size connection pool: channels are created lazily on demand and parked on return; at most
 * {@code poolSize} channels exist. {@link #borrow()} blocks while the pool is exhausted until
 * another operation returns its channel. Channels found dead on return or on borrow are evicted,
 * so a broken connection is never handed out twice. The SSH handshake runs outside the pool lock,
 * so slow connects never block concurrent releases.
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
    private boolean closed;

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
     * Borrows a channel, creating a new one when the pool is not yet at capacity and none is idle.
     * Blocks until a channel becomes available once the pool is at capacity; dead idle channels are
     * evicted instead of returned.
     *
     * @return borrowed live channel, never {@code null}
     * @throws IllegalStateException if the pool is closed or the connection cannot be established
     */
    SftpChannelFactory.PooledSftpChannel borrow() {
        while (true) {
            SftpChannelFactory.PooledSftpChannel channel = acquireIdleOrReserveSlot();
            if (channel != null) {
                return channel;
            }
            SftpChannelFactory.PooledSftpChannel createdChannel;
            try {
                createdChannel = factory.create(host, port, username, password, privateKeyPath, strictHostKeyChecking);
            } catch (Exception e) {
                rollbackSlot();
                throw new IllegalStateException(
                        "Cannot connect to SFTP " + username + "@" + host + ":" + port + ": " + e.getMessage(), e);
            }
            if (checkClosed(createdChannel)) {
                throw new IllegalStateException("SFTP connection pool is closed");
            }
            return createdChannel;
        }
    }

    /**
     * Returns a borrowed channel to the pool. A channel that was disconnected by its operation is
     * evicted instead, freeing a pool slot for the next borrower.
     *
     * @param channel previously borrowed channel
     */
    synchronized void release(SftpChannelFactory.PooledSftpChannel channel) {
        if (closed || !channel.alive()) {
            destroy(channel);
        } else {
            idle.addFirst(channel);
        }
        notifyAll();
    }

    /**
     * Closes every idle channel. Borrowed channels are closed by their users; later releases evict
     * instead of parking.
     */
    synchronized void close() {
        closed = true;
        while (!idle.isEmpty()) {
            destroy(idle.pollFirst());
        }
        notifyAll();
    }

    /**
     * Hands out an idle live channel, or reserves a free pool slot so the caller can establish a
     * fresh connection outside the lock ({@code null} return). Blocks while the pool is at
     * capacity and nothing is idle.
     */
    private synchronized SftpChannelFactory.PooledSftpChannel acquireIdleOrReserveSlot() {
        while (true) {
            if (closed) {
                throw new IllegalStateException("SFTP connection pool is closed");
            }
            SftpChannelFactory.PooledSftpChannel channel = idle.pollFirst();
            if (channel != null) {
                if (channel.alive()) {
                    return channel;
                }
                destroy(channel);
                continue;
            }
            if (created < poolSize) {
                created++;
                return null;
            }
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for an SFTP connection", e);
            }
        }
    }

    /** Gives a reserved slot back after a failed connection attempt. */
    private synchronized void rollbackSlot() {
        created--;
        notifyAll();
    }

    /** Re-checks the closed flag after connecting; disconnects the fresh channel when closed. */
    private synchronized boolean checkClosed(SftpChannelFactory.PooledSftpChannel createdChannel) {
        if (!closed) {
            return false;
        }
        createdChannel.close();
        created--;
        return true;
    }

    private void destroy(SftpChannelFactory.PooledSftpChannel channel) {
        channel.close();
        created--;
    }
}
