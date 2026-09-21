package io.github.cocosip.polystore.spring;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.DefaultStorageContainer;
import io.github.cocosip.polystore.StorageContainer;
import io.github.cocosip.polystore.StorageProvider;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Test backend of type {@code test}: every container gets its own {@link InMemoryStorageClient}. */
public final class TestStorageProvider implements StorageProvider {

    private final Map<String, InMemoryStorageClient> clients = new ConcurrentHashMap<>();

    @Override
    public String getType() {
        return "test";
    }

    @Override
    public StorageContainer createContainer(ContainerConfiguration config) {
        InMemoryStorageClient client = new InMemoryStorageClient();
        clients.put(config.getName(), client);
        return DefaultStorageContainer.from(config, client);
    }

    /**
     * Returns the client created for the given container name.
     *
     * @param containerName container name
     * @return client, or {@code null} if the container was not created yet
     */
    public InMemoryStorageClient clientFor(String containerName) {
        return clients.get(containerName);
    }
}
