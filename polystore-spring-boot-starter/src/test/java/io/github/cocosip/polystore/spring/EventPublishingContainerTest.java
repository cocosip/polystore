package io.github.cocosip.polystore.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.DefaultStorageContainer;
import io.github.cocosip.polystore.StorageContainer;
import io.github.cocosip.polystore.spring.event.FileDeletedEvent;
import io.github.cocosip.polystore.spring.event.FileSavedEvent;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventPublishingContainerTest {
    private static StorageContainer raw(InMemoryStorageClient backend) {
        return DefaultStorageContainer.from(
                ContainerConfiguration.builder().name("images").type("test").build(), backend);
    }

    @Test
    void successfulSaveAndExistingDeleteShouldPublishLogicalIds() {
        InMemoryStorageClient backend = new InMemoryStorageClient();
        List<Object> events = new ArrayList<>();
        EventPublishingContainer container = new EventPublishingContainer(raw(backend), events::add);

        assertThat(container.save("a.txt", new ByteArrayInputStream(new byte[0]), 0, ".txt"))
                .isEqualTo("a.txt");
        assertThat(container.delete("a.txt")).isTrue();
        assertThat(container.delete("missing.txt")).isFalse();

        assertThat(events).hasSize(2);
        assertThat(((FileSavedEvent) events.get(0)).getFileName()).isEqualTo("a.txt");
        assertThat(((FileDeletedEvent) events.get(1)).getFileName()).isEqualTo("a.txt");
    }

    @Test
    void tenantPrefixShouldNotLeakIntoSavedEvent() {
        InMemoryStorageClient backend = new InMemoryStorageClient();
        List<Object> events = new ArrayList<>();
        StorageContainer tenant = new TenantPathPrefixContainer(raw(backend), () -> "t1");
        EventPublishingContainer container = new EventPublishingContainer(tenant, events::add);

        container.save("a.txt", new ByteArrayInputStream(new byte[0]), 0, ".txt");

        assertThat(backend.store()).containsKey("t1/a.txt");
        assertThat(((FileSavedEvent) events.get(0)).getFileName()).isEqualTo("a.txt");
    }

    @Test
    void identityShouldDelegate() {
        EventPublishingContainer container =
                new EventPublishingContainer(raw(new InMemoryStorageClient()), event -> {});
        assertThat(container.getName()).isEqualTo("images");
        assertThat(container.getProviderType()).isEqualTo("test");
        assertThat(container.getConfiguration().getName()).isEqualTo("images");
    }
}
