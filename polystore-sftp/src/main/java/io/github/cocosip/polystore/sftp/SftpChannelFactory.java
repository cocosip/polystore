package io.github.cocosip.polystore.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

/**
 * Opens authenticated SFTP channels over JSch sessions. Called by the connection pool whenever a
 * new channel is needed.
 */
interface SftpChannelFactory {

    /**
     * Creates one connected, ready-to-use SFTP channel.
     *
     * @param host         SFTP host
     * @param port         SFTP port
     * @param username     user name
     * @param password     password, may be {@code null} when a private key is used
     * @param privateKeyPath private key file path, may be {@code null} when a password is used
     * @param strictHostKeyChecking JSch {@code StrictHostKeyChecking} value
     * @return pooled handle holding the channel and its session, never {@code null}
     * @throws Exception on connection or authentication failure
     */
    PooledSftpChannel create(
            String host,
            int port,
            String username,
            String password,
            String privateKeyPath,
            String strictHostKeyChecking)
            throws Exception;

    /** Channel plus the session that carries it, closed together on pool eviction. */
    final class PooledSftpChannel {

        private final ChannelSftp channel;
        private final Session session;

        PooledSftpChannel(ChannelSftp channel, Session session) {
            this.channel = channel;
            this.session = session;
        }

        ChannelSftp channel() {
            return channel;
        }

        void close() {
            channel.disconnect();
            if (session != null) {
                session.disconnect();
            }
        }
    }

    /** Default JSch-backed factory. */
    SftpChannelFactory JSCH = (host, port, username, password, privateKeyPath, strictHostKeyChecking) -> {
        JSch jsch = new JSch();
        if (privateKeyPath != null && !privateKeyPath.isEmpty()) {
            jsch.addIdentity(privateKeyPath);
        }
        Session session = jsch.getSession(username, host, port);
        if (password != null && !password.isEmpty()) {
            session.setPassword(password);
        }
        session.setConfig("StrictHostKeyChecking", strictHostKeyChecking);
        session.connect();
        ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect();
        return new PooledSftpChannel(channel, session);
    };
}
