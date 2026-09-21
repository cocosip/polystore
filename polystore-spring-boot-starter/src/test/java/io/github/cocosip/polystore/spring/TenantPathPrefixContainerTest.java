package io.github.cocosip.polystore.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.DefaultStorageContainer;
import io.github.cocosip.polystore.StorageContainer;
import io.github.cocosip.polystore.StorageSaveOptions;
import io.github.cocosip.polystore.TenantIdSupplier;
import io.github.cocosip.polystore.exception.TenantIdMissingException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TenantPathPrefixContainerTest {
    private static TenantPathPrefixContainer container(InMemoryStorageClient backend, TenantIdSupplier supplier) {
        StorageContainer raw = DefaultStorageContainer.from(
                ContainerConfiguration.builder().name("dicom").type("test").build(), backend);
        return new TenantPathPrefixContainer(raw, supplier);
    }

    @Test
    void saveShouldPreserveParametersAndUseSupplierTenantId() {
        InMemoryStorageClient backend = new InMemoryStorageClient();
        TenantPathPrefixContainer container = container(backend, () -> "t1");
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

        assertThat(container.save("a.txt", new ByteArrayInputStream(content), content.length, ".txt", true))
                .isEqualTo("t1/a.txt");
        assertThat(backend.text("t1/a.txt")).isEqualTo("hello");
    }

    @Test
    void saveShouldPreferExplicitTenantIdOverSupplier() {
        InMemoryStorageClient backend = new InMemoryStorageClient();
        TenantPathPrefixContainer container = container(backend, () -> "t1");
        StorageSaveOptions options = StorageSaveOptions.builder().tenantId("t2").build();

        container.save("a.txt", new ByteArrayInputStream(new byte[] {1}), 1, ".txt", false, options);

        assertThat(backend.store()).containsKey("t2/a.txt").doesNotContainKey("t1/a.txt");
    }

    @Test
    void missingTenantShouldFailAllOperations() {
        TenantPathPrefixContainer container = container(new InMemoryStorageClient(), null);
        assertThatThrownBy(() -> container.save("a.txt", new ByteArrayInputStream(new byte[0]), 0, ".txt"))
                .isInstanceOf(TenantIdMissingException.class);
        assertThatThrownBy(() -> container.getOrNull("a.txt")).isInstanceOf(TenantIdMissingException.class);
        assertThatThrownBy(() -> container.delete("a.txt")).isInstanceOf(TenantIdMissingException.class);
        assertThatThrownBy(() -> container.exists("a.txt")).isInstanceOf(TenantIdMissingException.class);
    }

    @Test
    void readAndAccessOperationsShouldApplyPrefix() throws Exception {
        InMemoryStorageClient backend = new InMemoryStorageClient();
        StorageContainer raw = DefaultStorageContainer.from(
                ContainerConfiguration.builder().name("dicom").type("test").build(), backend);
        raw.save("t1/a.txt", new byte[] {1}, ".txt");
        TenantPathPrefixContainer container = new TenantPathPrefixContainer(raw, () -> "t1");

        assertThat(container.exists("a.txt")).isTrue();
        assertThat(container.getAccessUrl("a.txt", Instant.parse("2026-09-22T00:00:00Z"), false))
                .isEqualTo("mem://t1/a.txt");
        try (InputStream in = container.get("a.txt")) {
            assertThat(in.readAllBytes()).containsExactly(1);
        }
        assertThat(container.delete("a.txt")).isTrue();
    }

    @Test
    void identityAndConfigurationShouldDelegate() {
        TenantPathPrefixContainer container = container(new InMemoryStorageClient(), () -> "t1");
        assertThat(container.getName()).isEqualTo("dicom");
        assertThat(container.getProviderType()).isEqualTo("test");
        assertThat(container.getConfiguration().getName()).isEqualTo("dicom");
    }
}
