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
     * @param configuration parsed connection parameters, never {@code null}
     * @return pooled handle holding the channel and its session, never {@code null}
     * @throws Exception on connection or authentication failure
     */
    PooledSftpChannel create(SftpStorageConfiguration configuration) throws Exception;

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

        /** Reports whether both the SFTP channel and its carrying session are still connected. */
        boolean alive() {
            return channel != null && channel.isConnected() && (session == null || session.isConnected());
        }

        void close() {
            channel.disconnect();
            if (session != null) {
                session.disconnect();
            }
        }
    }

    /** Default JSch-backed factory. */
    SftpChannelFactory JSCH = configuration -> {
        JSch jsch = new JSch();
        if (!configuration.knownHosts().isEmpty()) {
            jsch.setKnownHosts(configuration.knownHosts());
        }
        if (!configuration.privateKeyPath().isEmpty()) {
            jsch.addIdentity(configuration.privateKeyPath());
        }
        Session session = jsch.getSession(configuration.username(), configuration.host(), configuration.port());
        if (!configuration.password().isEmpty()) {
            session.setPassword(configuration.password());
        }
        session.setConfig("StrictHostKeyChecking", configuration.strictHostKeyChecking());
        // a bounded connect keeps a black-holed host from parking a reserved pool slot forever
        session.connect(configuration.connectTimeoutMillis());
        ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect(configuration.connectTimeoutMillis());
        return new PooledSftpChannel(channel, session);
    };
}
