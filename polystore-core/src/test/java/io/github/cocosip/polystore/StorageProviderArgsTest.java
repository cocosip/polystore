package io.github.cocosip.polystore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StorageProviderArgsTest {
    @Test
    void saveArgsShouldValidateLengthAndDefensivelyCopyMetadata() {
        ContainerConfiguration config =
                ContainerConfiguration.builder().name("files").type("test").build();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("key", "original");
        StorageProviderSaveArgs args = new StorageProviderSaveArgs(
                "files",
                config,
                "file-id",
                new ByteArrayInputStream(new byte[0]),
                0,
                ".dat",
                false,
                "application/octet-stream",
                metadata);
        metadata.put("key", "changed");

        assertThat(args.getMetadata()).containsEntry("key", "original");
        assertThatThrownBy(() -> args.getMetadata().put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new StorageProviderSaveArgs(
                        "files",
                        config,
                        "file-id",
                        new ByteArrayInputStream(new byte[0]),
                        -1,
                        ".dat",
                        false,
                        null,
                        Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contentLength");
    }

    @Test
    void baseArgsShouldRejectBlankIdentityValues() {
        ContainerConfiguration config =
                ContainerConfiguration.builder().name("files").type("test").build();
        assertThatThrownBy(() -> new StorageProviderDeleteArgs(" ", config, "id"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StorageProviderDeleteArgs("files", config, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saveOptionsShouldContainOnlyPolystoreExtensions() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("patient", "P1");
        StorageSaveOptions options = StorageSaveOptions.builder()
                .contentType("application/dicom")
                .tenantId("tenant-1")
                .metadata(metadata)
                .build();
        metadata.put("patient", "changed");
        assertThat(options.getContentType()).isEqualTo("application/dicom");
        assertThat(options.getTenantId()).isEqualTo("tenant-1");
        assertThat(options.getMetadata()).containsEntry("patient", "P1");
        assertThat(StorageSaveOptions.defaults().getMetadata()).isEmpty();
    }
}
