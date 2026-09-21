package io.github.cocosip.polystore.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.cocosip.polystore.ContainerConfiguration;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PolystoreBuilderTest {

    @Test
    void manualAssemblyShouldWorkEndToEnd() {
        TestStorageProvider provider = new TestStorageProvider();
        List<Object> events = new ArrayList<>();

        DefaultStorageManager manager = PolystoreBuilder.builder()
                .addProvider(provider)
                .addConfiguration(ContainerConfiguration.builder()
                        .name("images")
                        .type("test")
                        .isDefault(true)
                        .build())
                .addConfiguration(ContainerConfiguration.builder()
                        .name("dicom")
                        .type("test")
                        .tenantIsolation(io.github.cocosip.polystore.TenantIsolationMode.PATH_PREFIX)
                        .build())
                .tenantIdSupplier(() -> "tenant-a")
                .eventPublisher(events::add)
                .build();

        assertThat(manager.containerNames()).containsExactly("images", "dicom");
        manager.getDefaultContainer().save("a.txt", new ByteArrayInputStream(new byte[0]), 0, ".txt");
        manager.getContainer("dicom").save("b.txt", new ByteArrayInputStream(new byte[0]), 0, ".txt");

        assertThat(provider.clientFor("images").store()).containsKey("a.txt");
        assertThat(provider.clientFor("dicom").store()).containsKey("tenant-a/b.txt");
        assertThat(events).hasSize(2);
    }
}
