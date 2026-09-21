package io.github.cocosip.polystore.local;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.DefaultStorageContainer;
import io.github.cocosip.polystore.StorageClient;
import io.github.cocosip.polystore.StorageContainer;
import io.github.cocosip.polystore.StorageProvider;
import io.github.cocosip.polystore.util.ConfigUtils;
import java.nio.file.Paths;

/**
 * Storage provider of type {@code local}: stores files on the local filesystem.
 *
 * <p>Parameters: {@code basePath} (required, storage root), {@code urlPrefix} (default empty,
 * HTTP static-resource prefix returned by {@code getUrl}) and {@code createDirectories}
 * (default {@code true}, create missing directories on save).</p>
 */
public class LocalStorageProvider implements StorageProvider {

    /** Provider type identifier of this backend. */
    public static final String TYPE = "local";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public StorageContainer createContainer(ContainerConfiguration config) {
        var properties = config.getProperties();
        String basePath = ConfigUtils.requireString(properties, "basePath");
        String urlPrefix = ConfigUtils.optString(properties, "urlPrefix", "");
        boolean createDirectories = ConfigUtils.optBoolean(properties, "createDirectories", true);

        StorageClient client =
                new LocalStorageClient(Paths.get(basePath).toAbsolutePath().normalize(), urlPrefix, createDirectories);
        return DefaultStorageContainer.from(config, client);
    }
}
