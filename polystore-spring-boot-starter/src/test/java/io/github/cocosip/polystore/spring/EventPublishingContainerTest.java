package io.github.cocosip.polystore.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.DefaultStorageContainer;
import io.github.cocosip.polystore.SaveArgs;
import io.github.cocosip.polystore.StorageContainer;
import io.github.cocosip.polystore.spring.event.FileDeletedEvent;
import io.github.cocosip.polystore.spring.event.FileSavedEvent;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventPublishingContainerTest {

    private static StorageContainer raw(InMemoryStorageClient client) {
        return DefaultStorageContainer.from(
                ContainerConfiguration.builder().name("images").type("test").build(), client);
    }

    @Test
    void saveAndDeleteShouldPublishEventsWithLogicalNames() {
        InMemoryStorageClient client = new InMemoryStorageClient();
        List<Object> events = new ArrayList<>();
        EventPublishingContainer container = new EventPublishingContainer(raw(client), events::add);

        container.save("a.txt", new ByteArrayInputStream(new byte[0]), SaveArgs.defaults());
        container.delete("a.txt");
        container.deleteAll(List.of("b.txt", "c.txt"));

        assertThat(events).hasSize(4);
        FileSavedEvent saved = (FileSavedEvent) events.get(0);
        assertThat(saved.getContainerName()).isEqualTo("images");
        assertThat(saved.getFileName()).isEqualTo("a.txt");
        assertThat(saved.getProviderType()).isEqualTo("test");

        assertThat(events.get(1)).isInstanceOf(FileDeletedEvent.class);
        assertThat(events.get(2)).isInstanceOf(FileDeletedEvent.class);
        assertThat(events.get(3)).isInstanceOf(FileDeletedEvent.class);
        assertThat(((FileDeletedEvent) events.get(3)).getFileName()).isEqualTo("c.txt");
    }

    @Test
    void tenantPrefixShouldNotLeakIntoEventFileNames() {
        InMemoryStorageClient client = new InMemoryStorageClient();
        List<Object> events = new ArrayList<>();
        // Decorator order used by DefaultStorageManager: tenant inside, events outside.
        StorageContainer tenant = new TenantPathPrefixContainer(raw(client), () -> "t1");
        EventPublishingContainer container = new EventPublishingContainer(tenant, events::add);

        container.save("a.txt", new ByteArrayInputStream(new byte[0]), SaveArgs.defaults());

        assertThat(client.store()).containsKey("t1/a.txt");
        FileSavedEvent saved = (FileSavedEvent) events.get(0);
        assertThat(saved.getFileName()).isEqualTo("a.txt");
    }

    @Test
    void identityShouldDelegateToInnerContainer() {
        EventPublishingContainer container =
                new EventPublishingContainer(raw(new InMemoryStorageClient()), event -> {});

        assertThat(container.getName()).isEqualTo("images");
        assertThat(container.getProviderType()).isEqualTo("test");
    }
}
