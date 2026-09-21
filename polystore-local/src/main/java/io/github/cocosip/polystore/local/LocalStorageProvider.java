package io.github.cocosip.polystore.local;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.DefaultStorageContainer;
import io.github.cocosip.polystore.StorageClient;
import io.github.cocosip.polystore.StorageContainer;
import io.github.cocosip.polystore.StorageProvider;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

/**
 * Storage provider of type {@code local}: stores files on the local filesystem, mirroring SharpAbp's
 * {@code FileSystem} provider.
 *
 * <p>Parameters:</p>
 * <ul>
 *   <li>{@code basePath} (required, storage root directory)</li>
 *   <li>{@code appendContainerNameToBasePath} (default {@code true}, store files under a
 *       {@code containerName} sub-directory)</li>
 *   <li>{@code httpServer} (default empty, HTTP static-resource server prefixed to the URL returned
 *       by {@code getUrl})</li>
 *   <li>{@code createDirectories} (default {@code true}, create the missing base directory)</li>
 * </ul>
 */
public class LocalStorageProvider implements StorageProvider {

    /** Provider type identifier of this backend. */
    public static final String TYPE = "local";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public Collection<String> getAliases() {
        return List.of("FileSystem");
    }

    @Override
    public StorageContainer createContainer(ContainerConfiguration config) {
        LocalStorageConfiguration configuration = LocalStorageConfiguration.from(config);

        StorageClient client = new LocalStorageClient(
                Paths.get(configuration.basePath()).toAbsolutePath().normalize(),
                config.getName(),
                configuration.appendContainerNameToBasePath(),
                configuration.httpServer(),
                configuration.createDirectories());
        return DefaultStorageContainer.from(config, client);
    }
}
