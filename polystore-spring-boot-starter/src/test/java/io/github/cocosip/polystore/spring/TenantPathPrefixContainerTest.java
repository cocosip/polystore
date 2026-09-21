package io.github.cocosip.polystore.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.DefaultStorageContainer;
import io.github.cocosip.polystore.SaveArgs;
import io.github.cocosip.polystore.StorageContainer;
import io.github.cocosip.polystore.TenantIdSupplier;
import io.github.cocosip.polystore.UrlArgs;
import io.github.cocosip.polystore.exception.TenantIdMissingException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class TenantPathPrefixContainerTest {

    private static TenantPathPrefixContainer container(InMemoryStorageClient client, TenantIdSupplier supplier) {
        StorageContainer raw = DefaultStorageContainer.from(
                ContainerConfiguration.builder().name("dicom").type("test").build(), client);
        return new TenantPathPrefixContainer(raw, supplier);
    }

    private static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void saveShouldUseSupplierTenantId() {
        InMemoryStorageClient client = new InMemoryStorageClient();
        TenantPathPrefixContainer container = container(client, () -> "t1");

        container.save("a.txt", stream("hello"), SaveArgs.defaults());

        assertThat(client.store()).containsKey("t1/a.txt");
        assertThat(client.text("t1/a.txt")).isEqualTo("hello");
    }

    @Test
    void saveShouldPreferExplicitTenantIdOverSupplier() {
        InMemoryStorageClient client = new InMemoryStorageClient();
        TenantPathPrefixContainer container = container(client, () -> "t1");

        container.save("a.txt", stream("x"), SaveArgs.builder().tenantId("t2").build());

        assertThat(client.store()).containsKey("t2/a.txt");
        assertThat(client.store()).doesNotContainKey("t1/a.txt");
    }

    @Test
    void blankExplicitTenantIdShouldFallBackToSupplier() {
        InMemoryStorageClient client = new InMemoryStorageClient();
        TenantPathPrefixContainer container = container(client, () -> "t1");

        container.save("a.txt", stream("x"), SaveArgs.builder().tenantId("  ").build());

        assertThat(client.store()).containsKey("t1/a.txt");
    }

    @Test
    void missingTenantIdOnSaveShouldThrow() {
        TenantPathPrefixContainer container = container(new InMemoryStorageClient(), null);

        assertThatThrownBy(() -> container.save("a.txt", stream("x"), SaveArgs.defaults()))
                .isInstanceOf(TenantIdMissingException.class)
                .hasMessageContaining("dicom");
    }

    @Test
    void supplierReturningNullShouldThrowOnReadOperations() {
        TenantPathPrefixContainer container = container(new InMemoryStorageClient(), () -> null);

        assertThatThrownBy(() -> container.get("a.txt")).isInstanceOf(TenantIdMissingException.class);
        assertThatThrownBy(() -> container.delete("a.txt")).isInstanceOf(TenantIdMissingException.class);
        assertThatThrownBy(() -> container.exists("a.txt")).isInstanceOf(TenantIdMissingException.class);
        assertThatThrownBy(() -> container.deleteAll(List.of("a.txt"))).isInstanceOf(TenantIdMissingException.class);
    }

    @Test
    void readOperationsShouldApplyPrefix() throws Exception {
        InMemoryStorageClient client = new InMemoryStorageClient();
        client.save("t1/a.txt", stream("x"), SaveArgs.defaults());
        TenantPathPrefixContainer container = container(client, () -> "t1");

        assertThat(container.exists("a.txt")).isTrue();
        assertThat(container.getUrl("a.txt", UrlArgs.defaults())).isEqualTo("mem://t1/a.txt");
        try (InputStream in = container.get("a.txt")) {
            assertThat(in.readAllBytes()).isNotEmpty();
        }
        container.delete("a.txt");
        assertThat(client.store()).isEmpty();
    }

    @Test
    void deleteAllShouldPrefixEveryName() {
        InMemoryStorageClient client = new InMemoryStorageClient();
        client.save("t1/a.txt", stream("x"), SaveArgs.defaults());
        client.save("t1/b.txt", stream("x"), SaveArgs.defaults());
        TenantPathPrefixContainer container = container(client, () -> "t1");

        container.deleteAll(List.of("a.txt", "b.txt"));

        assertThat(client.store()).isEmpty();
    }

    @Test
    void identityShouldDelegateToInnerContainer() {
        TenantPathPrefixContainer container = container(new InMemoryStorageClient(), () -> "t1");

        assertThat(container.getName()).isEqualTo("dicom");
        assertThat(container.getProviderType()).isEqualTo("test");
        assertThat(container.getInfo().getName()).isEqualTo("dicom");
    }
}
