package io.github.cocosip.polystore.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.StorageProviderAccessArgs;
import io.github.cocosip.polystore.StorageProviderDeleteArgs;
import io.github.cocosip.polystore.StorageProviderExistsArgs;
import io.github.cocosip.polystore.StorageProviderGetArgs;
import io.github.cocosip.polystore.StorageProviderSaveArgs;
import io.github.cocosip.polystore.exception.StorageFileAlreadyExistsException;
import io.github.cocosip.polystore.exception.StorageOperationException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalStorageClientTest {
    private final Path basePath = Path.of("target", "local-storage-test");
    private final ContainerConfiguration config =
            ContainerConfiguration.builder().name("images").type("local").build();

    @BeforeEach
    void prepareDirectory() throws Exception {
        Files.createDirectories(basePath);
        try (var paths = Files.walk(basePath)) {
            paths.sorted(java.util.Comparator.reverseOrder())
                    .filter(path -> !path.equals(basePath))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private LocalStorageClient backend() {
        return new LocalStorageClient(basePath, "images", true, "", true);
    }

    private StorageProviderSaveArgs saveArgs(String fileId, String text, long length, boolean overrideExisting) {
        return new StorageProviderSaveArgs(
                "images",
                config,
                fileId,
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),
                length,
                ".txt",
                overrideExisting,
                "text/plain",
                Map.of());
    }

    @Test
    void saveGetExistsDeleteShouldRoundTrip() throws Exception {
        LocalStorageClient backend = backend();
        assertThat(backend.save(saveArgs("2026/a.txt", "hello", 5, false))).isEqualTo("2026/a.txt");
        assertThat(backend.exists(new StorageProviderExistsArgs("images", config, "2026/a.txt")))
                .isTrue();
        try (var stream = backend.getOrNull(new StorageProviderGetArgs("images", config, "2026/a.txt"))) {
            assertThat(stream.readAllBytes()).asString(StandardCharsets.UTF_8).isEqualTo("hello");
        }
        assertThat(backend.delete(new StorageProviderDeleteArgs("images", config, "2026/a.txt")))
                .isTrue();
        assertThat(backend.delete(new StorageProviderDeleteArgs("images", config, "2026/a.txt")))
                .isFalse();
    }

    @Test
    void saveShouldHonorOverwriteAndDeclaredLength() throws Exception {
        LocalStorageClient backend = backend();
        backend.save(saveArgs("a.txt", "first", 5, false));
        assertThatThrownBy(() -> backend.save(saveArgs("a.txt", "second", 6, false)))
                .isInstanceOf(StorageFileAlreadyExistsException.class);

        backend.save(saveArgs("a.txt", "second-extra", 6, true));
        assertThat(Files.readString(basePath.resolve("images/a.txt"))).isEqualTo("second");
        assertThatThrownBy(() -> backend.save(saveArgs("short.txt", "x", 2, false)))
                .isInstanceOf(StorageOperationException.class)
                .hasRootCauseInstanceOf(java.io.EOFException.class);
    }

    @Test
    void accessUrlAndPathTraversalShouldFollowConfiguration() {
        LocalStorageClient backend = new LocalStorageClient(basePath, "images", true, "https://cdn.example.com", true);
        assertThat(backend.getAccessUrl(new StorageProviderAccessArgs(
                        "images", config, "a.txt", Instant.parse("2026-09-22T00:00:00Z"), false)))
                .isEqualTo("https://cdn.example.com/images/a.txt");
        assertThatThrownBy(() -> backend.save(saveArgs("../../escape.txt", "x", 1, false)))
                .isInstanceOf(StorageOperationException.class)
                .hasMessageContaining("escapes");
    }

    @Test
    void providerShouldParseSharpAbpKeysAndRejectMissingBasePath() {
        LocalStorageProvider provider = new LocalStorageProvider();
        assertThat(provider.getAliases()).contains("FileSystem");
        assertThat(provider.createBackend(ContainerConfiguration.builder()
                        .name("images")
                        .type("local")
                        .properties(Map.of(
                                "basePath",
                                basePath.toString(),
                                "appendContainerNameToBasePath",
                                false,
                                "createDirectories",
                                true))
                        .build()))
                .isInstanceOf(LocalStorageClient.class);
        assertThatThrownBy(() -> provider.createBackend(ContainerConfiguration.builder()
                        .name("images")
                        .type("local")
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("basePath");
    }
}
