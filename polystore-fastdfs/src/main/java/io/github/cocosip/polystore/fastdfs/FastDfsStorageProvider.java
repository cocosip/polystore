package io.github.cocosip.polystore.fastdfs;

import com.github.tobato.fastdfs.service.FastFileStorageClient;
import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.DefaultStorageContainer;
import io.github.cocosip.polystore.StorageContainer;
import io.github.cocosip.polystore.StorageProvider;
import io.github.cocosip.polystore.util.ConfigUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Storage provider of type {@code fastdfs}.
 *
 * <p>Parameters: {@code trackerServers} (required, comma-separated {@code host:port} list),
 * {@code connectTimeout} (default 5000 ms), {@code networkTimeout} (default 30000 ms),
 * {@code charset} (default UTF-8), {@code urlPrefix} (default empty, Nginx proxy address
 * prefixed to file ids) and {@code indexPath} (default {@code ~/.polystore/fastdfs-{name}.index}
 * — the logical-name to file-id mapping file; see {@link FastDfsStorageClient}).</p>
 */
public class FastDfsStorageProvider implements StorageProvider {

    /** Provider type identifier of this backend. */
    public static final String TYPE = "fastdfs";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public StorageContainer createContainer(ContainerConfiguration config) {
        var properties = config.getProperties();
        String trackerServers = ConfigUtils.requireString(properties, "trackerServers");
        int connectTimeout = ConfigUtils.optInt(properties, "connectTimeout", 5000);
        int networkTimeout = ConfigUtils.optInt(properties, "networkTimeout", 30000);
        String charset = ConfigUtils.optString(properties, "charset", "UTF-8");
        String urlPrefix = ConfigUtils.optString(properties, "urlPrefix", "");
        String indexPath = ConfigUtils.optString(
                properties,
                "indexPath",
                System.getProperty("user.home") + "/.polystore/fastdfs-" + config.getName() + ".index");

        List<String> trackers = TobatoClientFactory.splitTrackerServers(trackerServers);
        if (trackers.isEmpty()) {
            throw new IllegalStateException("Missing required storage parameter 'trackerServers'");
        }
        FastFileStorageClient client = TobatoClientFactory.create(trackers, connectTimeout, networkTimeout, charset);
        Path indexFile = Paths.get(indexPath);
        return DefaultStorageContainer.from(
                config, new FastDfsStorageClient(client, new LocalFileIndex(indexFile), urlPrefix));
    }
}
