package io.github.cocosip.polystore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cocosip.polystore.exception.StorageFileNotFoundException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DefaultStorageContainerTest {
    private static final ContainerConfiguration CONFIGURATION =
            ContainerConfiguration.builder().name("images").type("local").build();

    private static final class RecordingBackend implements StorageBackend {
        StorageProviderSaveArgs saveArgs;
        StorageProviderDeleteArgs deleteArgs;
        StorageProviderExistsArgs existsArgs;
        StorageProviderDownloadArgs downloadArgs;
        StorageProviderGetArgs getArgs;
        StorageProviderAccessArgs accessArgs;
        InputStream content;

        @Override
        public String save(StorageProviderSaveArgs args) {
            saveArgs = args;
            return args.getFileId();
        }

        @Override
        public boolean delete(StorageProviderDeleteArgs args) {
            deleteArgs = args;
            return true;
        }

        @Override
        public boolean exists(StorageProviderExistsArgs args) {
            existsArgs = args;
            return true;
        }

        @Override
        public boolean download(StorageProviderDownloadArgs args) {
            downloadArgs = args;
            return true;
        }

        @Override
        public InputStream getOrNull(StorageProviderGetArgs args) {
            getArgs = args;
            return content;
        }

        @Override
        public String getAccessUrl(StorageProviderAccessArgs args) {
            accessArgs = args;
            return "mem://file";
        }
    }

    @Test
    void shouldBuildSaveArgsAndReturnLogicalFileId() {
        RecordingBackend backend = new RecordingBackend();
        DefaultStorageContainer container = DefaultStorageContainer.from(CONFIGURATION, backend);
        InputStream stream = new ByteArrayInputStream(new byte[] {1, 2, 3});
        StorageSaveOptions options = StorageSaveOptions.builder()
                .contentType("image/png")
                .metadata(java.util.Map.of("source", "test"))
                .build();

        assertThat(container.save("logical-id", stream, 3, ".png", true, options))
                .isEqualTo("logical-id");
        assertThat(backend.saveArgs.getContainerName()).isEqualTo("images");
        assertThat(backend.saveArgs.getConfiguration()).isSameAs(CONFIGURATION);
        assertThat(backend.saveArgs.getFileId()).isEqualTo("logical-id");
        assertThat(backend.saveArgs.getFileStream()).isSameAs(stream);
        assertThat(backend.saveArgs.getContentLength()).isEqualTo(3);
        assertThat(backend.saveArgs.getFileExt()).isEqualTo(".png");
        assertThat(backend.saveArgs.isOverrideExisting()).isTrue();
        assertThat(backend.saveArgs.getContentType()).isEqualTo("image/png");
        assertThat(backend.saveArgs.getMetadata()).containsEntry("source", "test");
    }

    @Test
    void canonicalSaveShouldDefaultOverrideExistingToFalse() {
        RecordingBackend backend = new RecordingBackend();
        DefaultStorageContainer container = DefaultStorageContainer.from(CONFIGURATION, backend);

        container.save("file", new ByteArrayInputStream(new byte[0]), 0, ".bin");

        assertThat(backend.saveArgs.isOverrideExisting()).isFalse();
        assertThat(backend.saveArgs.getContentType()).isNull();
        assertThat(backend.saveArgs.getMetadata()).isEmpty();
    }

    @Test
    void shouldBuildArgsForRemainingOperations() {
        RecordingBackend backend = new RecordingBackend();
        backend.content = new ByteArrayInputStream(new byte[] {9});
        DefaultStorageContainer container = DefaultStorageContainer.from(CONFIGURATION, backend);
        Instant expires = Instant.parse("2026-09-22T00:00:00Z");
        Path path = Path.of("build", "download.bin");

        assertThat(container.delete("delete-id")).isTrue();
        assertThat(container.exists("exists-id")).isTrue();
        assertThat(container.download("download-id", path)).isTrue();
        assertThat(container.getOrNull("get-id")).isSameAs(backend.content);
        assertThat(container.getAccessUrl("url-id", expires, true)).isEqualTo("mem://file");
        assertThat(backend.deleteArgs.getFileId()).isEqualTo("delete-id");
        assertThat(backend.existsArgs.getFileId()).isEqualTo("exists-id");
        assertThat(backend.downloadArgs.getPath()).isEqualTo(path);
        assertThat(backend.getArgs.getFileId()).isEqualTo("get-id");
        assertThat(backend.accessArgs.getExpires()).isEqualTo(expires);
        assertThat(backend.accessArgs.isCheckFileExist()).isTrue();
    }

    @Test
    void getShouldThrowButGetOrNullShouldReturnNullForMissingFile() {
        DefaultStorageContainer container = DefaultStorageContainer.from(CONFIGURATION, new RecordingBackend());
        assertThat(container.getOrNull("missing")).isNull();
        assertThatThrownBy(() -> container.get("missing"))
                .isInstanceOf(StorageFileNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void shouldExposeConfigurationAndIdentity() {
        DefaultStorageContainer container = DefaultStorageContainer.from(CONFIGURATION, new RecordingBackend());
        assertThat(container.getConfiguration()).isSameAs(CONFIGURATION);
        assertThat(container.getName()).isEqualTo("images");
        assertThat(container.getProviderType()).isEqualTo("local");
        assertThat(container.getInfo().getName()).isEqualTo("images");
    }

    @Test
    void disabledHttpAccessShouldReturnEmptyUrlWithoutCallingBackend() {
        ContainerConfiguration config = ContainerConfiguration.builder()
                .name("private")
                .type("local")
                .httpAccess(false)
                .build();
        RecordingBackend backend = new RecordingBackend();
        DefaultStorageContainer container = DefaultStorageContainer.from(config, backend);

        assertThat(container.getAccessUrl("id", Instant.parse("2026-09-22T00:00:00Z"), true))
                .isEmpty();
        assertThat(backend.accessArgs).isNull();
    }
}
