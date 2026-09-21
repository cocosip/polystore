package io.github.cocosip.polystore.fastdfs;

import com.github.tobato.fastdfs.domain.conn.FdfsConnectionManager;
import com.github.tobato.fastdfs.domain.conn.FdfsConnectionPool;
import com.github.tobato.fastdfs.domain.conn.PooledConnectionFactory;
import com.github.tobato.fastdfs.domain.conn.TrackerConnectionManager;
import com.github.tobato.fastdfs.service.DefaultFastFileStorageClient;
import com.github.tobato.fastdfs.service.DefaultTrackerClient;
import com.github.tobato.fastdfs.service.FastFileStorageClient;
import com.github.tobato.fastdfs.service.TrackerClient;
import io.github.cocosip.polystore.exception.StorageOperationException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

/**
 * Assembles the tobato {@link FastFileStorageClient} without Spring: the library wires its
 * collaborators through field injection, so this factory constructs the connection pool, the
 * tracker connection manager and the tracker client by hand and injects them into the client
 * instance (subclasses reach the protected fields; one private field on
 * {@link DefaultTrackerClient} is set reflectively).
 */
final class TobatoClientFactory {

    private TobatoClientFactory() {}

    /**
     * Builds a storage client for the given tracker servers.
     *
     * @param trackerServers  tracker addresses in {@code host:port} form, never empty
     * @param connectTimeout  connect timeout in milliseconds
     * @param networkTimeout  socket read timeout in milliseconds
     * @param charsetName     protocol charset
     * @return ready storage client, never {@code null}
     * @throws StorageOperationException if the tracker list is malformed or assembly fails
     */
    static FastFileStorageClient create(
            List<String> trackerServers, int connectTimeout, int networkTimeout, String charsetName) {
        PooledConnectionFactory connectionFactory = new PooledConnectionFactory();
        connectionFactory.setConnectTimeout(connectTimeout);
        connectionFactory.setSoTimeout(networkTimeout);
        connectionFactory.setCharsetName(charsetName);

        TrackerConnectionManager connectionManager =
                new TrackerConnectionManager(new FdfsConnectionPool(connectionFactory));
        connectionManager.setTrackerList(trackerServers);
        try {
            connectionManager.initTracker();
        } catch (Exception e) {
            throw new StorageOperationException("Invalid tracker servers " + trackerServers + ": " + e.getMessage(), e);
        }

        DefaultTrackerClient trackerClient = new DefaultTrackerClient();
        setField(trackerClient, "trackerConnectionManager", connectionManager);

        return new InjectedFastFileStorageClient(trackerClient, connectionManager);
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new StorageOperationException("Cannot assemble FastDFS client (field " + fieldName + ")", e);
        }
    }

    /** Injects the protected fields that tobato normally populates via Spring. */
    private static final class InjectedFastFileStorageClient extends DefaultFastFileStorageClient {

        private InjectedFastFileStorageClient(TrackerClient trackerClient, FdfsConnectionManager connectionManager) {
            this.trackerClient = trackerClient;
            this.fdfsConnectionManager = connectionManager;
        }
    }

    static List<String> splitTrackerServers(String trackerServers) {
        return Arrays.stream(trackerServers.split("[,;]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
