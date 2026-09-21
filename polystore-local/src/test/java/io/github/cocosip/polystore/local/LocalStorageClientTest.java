package io.github.cocosip.polystore.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.SaveArgs;
import io.github.cocosip.polystore.UrlArgs;
import io.github.cocosip.polystore.exception.StorageFileAlreadyExistsException;
import io.github.cocosip.polystore.exception.StorageFileNotFoundException;
import io.github.cocosip.polystore.exception.StorageOperationException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalStorageClientTest {

    @TempDir
    Path tempDir;

    private LocalStorageClient client() {
        return new LocalStorageClient(tempDir, "", true);
    }

    private static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void saveAndGetShouldRoundTripContent() throws Exception {
        LocalStorageClient client = client();

        client.save("hello.txt", stream("hello world"), SaveArgs.defaults());

        assertThat(Files.readString(tempDir.resolve("hello.txt"))).isEqualTo("hello world");
        try (InputStream in = client.get("hello.txt")) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello world");
        }
    }

    @Test
    void saveShouldCreateMissingSubdirectories() {
        LocalStorageClient client = client();

        client.save("2024/01/scan.dcm", stream("dcm"), SaveArgs.defaults());

        assertThat(tempDir.resolve("2024/01/scan.dcm")).exists();
        assertThat(client.exists("2024/01/scan.dcm")).isTrue();
    }

    @Test
    void overwriteTrueShouldReplaceAndFalseShouldReject() throws Exception {
        LocalStorageClient client = client();
        client.save("a.txt", stream("v1"), SaveArgs.defaults());

        client.save("a.txt", stream("v2"), SaveArgs.defaults());
        assertThat(Files.readString(tempDir.resolve("a.txt"))).isEqualTo("v2");

        assertThatThrownBy(() -> client.save(
                        "a.txt",
                        stream("v3"),
                        SaveArgs.builder().overwrite(false).build()))
                .isInstanceOf(StorageFileAlreadyExistsException.class);
        assertThat(Files.readString(tempDir.resolve("a.txt"))).isEqualTo("v2");
    }

    @Test
    void getMissingFileShouldThrowNotFound() {
        assertThatThrownBy(() -> client().get("nope.txt")).isInstanceOf(StorageFileNotFoundException.class);
    }

    @Test
    void deleteShouldBeSilentForMissingFiles() {
        LocalStorageClient client = client();
        client.save("a.txt", stream("x"), SaveArgs.defaults());

        client.delete("a.txt");
        client.delete("a.txt"); // already gone

        assertThat(client.exists("a.txt")).isFalse();
    }

    @Test
    void deleteAllShouldRemoveEveryListedFile() {
        LocalStorageClient client = client();
        client.save("a.txt", stream("x"), SaveArgs.defaults());
        client.save("b.txt", stream("x"), SaveArgs.defaults());

        client.deleteAll(List.of("a.txt", "b.txt", "missing.txt"));

        assertThat(client.exists("a.txt")).isFalse();
        assertThat(client.exists("b.txt")).isFalse();
    }

    @Test
    void getUrlShouldComposePrefixAndFileName() {
        assertThat(new LocalStorageClient(tempDir, "", true).getUrl("a.txt", UrlArgs.defaults()))
                .isEqualTo("a.txt");
        assertThat(new LocalStorageClient(tempDir, "https://cdn.example.com/images", true)
                        .getUrl("2024/a.txt", UrlArgs.defaults()))
                .isEqualTo("https://cdn.example.com/images/2024/a.txt");
    }

    @Test
    void pathTraversalShouldBeRejected() {
        LocalStorageClient client = client();

        assertThatThrownBy(() -> client.save("../escape.txt", stream("x"), SaveArgs.defaults()))
                .isInstanceOf(StorageOperationException.class)
                .hasMessageContaining("escapes");
        assertThatThrownBy(() -> client.get("../escape.txt")).isInstanceOf(StorageOperationException.class);
    }

    @Test
    void missingBaseDirectoryShouldBeCreatedOnlyWhenEnabled() throws Exception {
        Path base = tempDir.resolve("created");
        new LocalStorageClient(base, "", true);

        assertThat(base).exists();

        Path lazy = tempDir.resolve("lazy");
        LocalStorageClient client = new LocalStorageClient(lazy, "", false);
        assertThat(lazy).doesNotExist();

        client.save("a.txt", stream("x"), SaveArgs.defaults());
        assertThat(lazy.resolve("a.txt")).exists();
    }

    @Test
    void providerShouldBuildContainerFromConfiguration() {
        LocalStorageProvider provider = new LocalStorageProvider();

        assertThat(provider.getType()).isEqualTo("local");
        var container = provider.createContainer(ContainerConfiguration.builder()
                .name("images")
                .type("local")
                .isDefault(true)
                .property("basePath", tempDir.toString())
                .property("urlPrefix", "https://cdn.example.com")
                .build());

        assertThat(container.getName()).isEqualTo("images");
        assertThat(container.getInfo().isDefault()).isTrue();
        assertThat(container.getUrl("a/b.txt", UrlArgs.defaults())).isEqualTo("https://cdn.example.com/a/b.txt");
    }

    @Test
    void providerShouldRejectMissingBasePath() {
        LocalStorageProvider provider = new LocalStorageProvider();

        assertThatThrownBy(() -> provider.createContainer(ContainerConfiguration.builder()
                        .name("images")
                        .type("local")
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("basePath");
    }
}
