package io.github.cocosip.polystore.spring;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.DefaultStorageContainer;
import io.github.cocosip.polystore.StorageContainer;
import io.github.cocosip.polystore.StorageProvider;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Test backend of type {@code test}: every container gets its own {@link InMemoryStorageClient}. */
public final class TestStorageProvider implements StorageProvider {

    private final Map<String, InMemoryStorageClient> clients = new ConcurrentHashMap<>();
    private final String type;
    private final List<String> aliases;

    /** Creates a provider of type {@code test}. */
    public TestStorageProvider() {
        this("test");
    }

    /**
     * Creates a provider with an explicit type identifier.
     *
     * @param type provider type identifier
     */
    public TestStorageProvider(String type) {
        this(type, List.of());
    }

    /**
     * Creates a provider with an explicit type identifier and alias identifiers.
     *
     * @param type    provider type identifier
     * @param aliases additional type identifiers the provider answers to
     */
    public TestStorageProvider(String type, List<String> aliases) {
        this.type = type;
        this.aliases = List.copyOf(aliases);
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Collection<String> getAliases() {
        return aliases;
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
