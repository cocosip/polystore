package io.github.cocosip.polystore.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.StorageContainer;
import io.github.cocosip.polystore.StorageManager;
import io.github.cocosip.polystore.TenantIsolationMode;
import io.github.cocosip.polystore.exception.ContainerNotFoundException;
import io.github.cocosip.polystore.exception.StorageProviderNotFoundException;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultStorageManagerTest {

    private static ContainerConfiguration config(String name, String type) {
        return ContainerConfiguration.builder().name(name).type(type).build();
    }

    private static DefaultStorageManager manager(ContainerConfiguration... configurations) {
        return new DefaultStorageManager(List.of(configurations), List.of(new TestStorageProvider()), null, null);
    }

    @Test
    void shouldCreateContainersAndExposeNames() {
        StorageManager manager = manager(config("images", "test"), config("dicom", "test"));

        assertThat(manager.containerNames()).containsExactly("images", "dicom");
        assertThat(manager.getContainer("images").getProviderType()).isEqualTo("test");
    }

    @Test
    void unknownContainerShouldThrow() {
        StorageManager manager = manager(config("images", "test"));

        assertThatThrownBy(() -> manager.getContainer("nope")).isInstanceOf(ContainerNotFoundException.class);
    }

    @Test
    void defaultContainerShouldFollowConfigurationFlag() {
        DefaultStorageManager manager = new DefaultStorageManager(
                List.of(
                        ContainerConfiguration.builder()
                                .name("images")
                                .type("test")
                                .isDefault(true)
                                .build(),
                        config("dicom", "test")),
                List.of(new TestStorageProvider()),
                null,
                null);

        assertThat(manager.getDefaultContainer().getName()).isEqualTo("images");
    }

    @Test
    void missingDefaultContainerShouldThrow() {
        StorageManager manager = manager(config("images", "test"));

        assertThatThrownBy(manager::getDefaultContainer).isInstanceOf(ContainerNotFoundException.class);
    }

    @Test
    void moreThanOneDefaultShouldBeRejected() {
        ContainerConfiguration first = ContainerConfiguration.builder()
                .name("a")
                .type("test")
                .isDefault(true)
                .build();
        ContainerConfiguration second = ContainerConfiguration.builder()
                .name("b")
                .type("test")
                .isDefault(true)
                .build();

        assertThatThrownBy(() -> manager(first, second))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("default");
    }

    @Test
    void duplicateContainerNamesShouldBeRejected() {
        assertThatThrownBy(() -> manager(config("images", "test"), config("images", "test")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void unregisteredProviderTypeShouldBeRejected() {
        assertThatThrownBy(() -> manager(config("images", "Minio")))
                .isInstanceOf(StorageProviderNotFoundException.class)
                .hasMessageContaining("Minio")
                .satisfies(throwable -> assertThat(((StorageProviderNotFoundException) throwable).getProviderType())
                        .isEqualTo("Minio"));
    }

    @Test
    void providerTypeLookupShouldIgnoreCase() {
        TestStorageProvider provider = new TestStorageProvider("minio");
        DefaultStorageManager manager = new DefaultStorageManager(
                List.of(ContainerConfiguration.builder()
                        .name("dicom")
                        .type("Minio")
                        .build()),
                List.of(provider),
                null,
                null);

        StorageContainer container = manager.getContainer("dicom");
        assertThat(container.getProviderType()).isEqualTo("Minio");

        container.save("a.txt", new ByteArrayInputStream(new byte[0]), 0, ".txt");

        assertThat(provider.clientFor("dicom").store()).containsKey("a.txt");
    }

    @Test
    void providerAliasShouldResolveTheConfiguredType() {
        TestStorageProvider provider = new TestStorageProvider("local", List.of("FileSystem"));
        DefaultStorageManager manager = new DefaultStorageManager(
                List.of(ContainerConfiguration.builder()
                        .name("images")
                        .type("FileSystem")
                        .build()),
                List.of(provider),
                null,
                null);

        StorageContainer container = manager.getContainer("images");
        assertThat(container.getProviderType()).isEqualTo("FileSystem");
        container.save("a.txt", new ByteArrayInputStream(new byte[0]), 0, ".txt");
        assertThat(provider.clientFor("images").store()).containsKey("a.txt");
    }

    @Test
    void canonicalTypeShouldWinOverAnotherProvidersAlias() {
        DefaultStorageManager manager = new DefaultStorageManager(
                List.of(ContainerConfiguration.builder()
                        .name("images")
                        .type("FileSystem")
                        .build()),
                List.of(new TestStorageProvider("local", List.of("FileSystem")), new TestStorageProvider("filesystem")),
                null,
                null);

        assertThat(manager.getContainer("images").getProviderType()).isEqualTo("FileSystem");
    }

    @Test
    void pathPrefixContainerShouldApplyTenantPrefix() {
        TestStorageProvider provider = new TestStorageProvider();
        DefaultStorageManager manager = new DefaultStorageManager(
                List.of(ContainerConfiguration.builder()
                        .name("dicom")
                        .type("test")
                        .tenantIsolation(TenantIsolationMode.PATH_PREFIX)
                        .build()),
                List.of(provider),
                () -> "tenant-a",
                null);

        StorageContainer container = manager.getContainer("dicom");
        container.save("scan.dcm", new ByteArrayInputStream(new byte[0]), 0, ".dcm");

        assertThat(provider.clientFor("dicom").store()).containsKey("tenant-a/scan.dcm");
        assertThat(container.exists("scan.dcm")).isTrue();
    }

    @Test
    void noneContainerShouldNotPrefix() {
        TestStorageProvider provider = new TestStorageProvider();
        DefaultStorageManager manager =
                new DefaultStorageManager(List.of(config("images", "test")), List.of(provider), () -> "tenant-a", null);

        manager.getContainer("images").save("a.txt", new ByteArrayInputStream(new byte[0]), 0, ".txt");

        assertThat(provider.clientFor("images").store()).containsKey("a.txt");
    }
}
