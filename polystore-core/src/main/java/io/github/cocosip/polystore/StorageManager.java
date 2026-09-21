package io.github.cocosip.polystore;

import io.github.cocosip.polystore.exception.ContainerNotFoundException;
import java.util.Collection;

/**
 * Global entry point: holds every configured {@link StorageContainer} and hands them out by name.
 */
public interface StorageManager {

    /**
     * Returns the container with the given name.
     *
     * @param name container name, never {@code null}
     * @return the matching container, never {@code null}
     * @throws ContainerNotFoundException if no container is registered under this name
     */
    StorageContainer getContainer(String name);

    /**
     * Returns the container marked as default in the configuration.
     *
     * @return the default container, never {@code null}
     * @throws ContainerNotFoundException if no container is marked as default
     */
    StorageContainer getDefaultContainer();

    /**
     * Returns the names of all registered containers.
     *
     * @return container names, never {@code null}
     */
    Collection<String> containerNames();
}
