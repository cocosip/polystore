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
        return new LocalStorageClient(tempDir, "images", true, "", true);
    }

    private static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void saveAndGetShouldRoundTripContent() throws Exception {
        LocalStorageClient client = client();

        client.save("hello.txt", stream("hello world"), SaveArgs.defaults());

        assertThat(Files.readString(tempDir.resolve("images/hello.txt"))).isEqualTo("hello world");
        try (InputStream in = client.get("hello.txt")) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello world");
        }
    }

    @Test
    void saveShouldCreateMissingSubdirectories() {
        LocalStorageClient client = client();

        client.save("2024/01/scan.dcm", stream("dcm"), SaveArgs.defaults());

        assertThat(tempDir.resolve("images/2024/01/scan.dcm")).exists();
        assertThat(client.exists("2024/01/scan.dcm")).isTrue();
    }

    @Test
    void overwriteTrueShouldReplaceAndFalseShouldReject() throws Exception {
        LocalStorageClient client = client();
        client.save("a.txt", stream("v1"), SaveArgs.defaults());

        client.save("a.txt", stream("v2"), SaveArgs.defaults());
        assertThat(Files.readString(tempDir.resolve("images/a.txt"))).isEqualTo("v2");

        assertThatThrownBy(() -> client.save(
                        "a.txt",
                        stream("v3"),
                        SaveArgs.builder().overwrite(false).build()))
                .isInstanceOf(StorageFileAlreadyExistsException.class);
        assertThat(Files.readString(tempDir.resolve("images/a.txt"))).isEqualTo("v2");
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
    void containerNameShouldBeAppendedOnlyWhenEnabled() {
        LocalStorageClient appending = new LocalStorageClient(tempDir, "images", true, "", true);
        appending.save("a.txt", stream("x"), SaveArgs.defaults());

        LocalStorageClient flat = new LocalStorageClient(tempDir, "images", false, "", true);
        flat.save("a.txt", stream("y"), SaveArgs.defaults());

        assertThat(tempDir.resolve("images/a.txt")).exists();
        assertThat(tempDir.resolve("a.txt")).exists();
    }

    @Test
    void getUrlWithoutHttpServerShouldReturnRelativePath() {
        LocalStorageClient appending = new LocalStorageClient(tempDir, "images", true, "", true);
        LocalStorageClient flat = new LocalStorageClient(tempDir, "images", false, "", true);

        assertThat(appending.getUrl("2024/a.txt", UrlArgs.defaults())).isEqualTo("images/2024/a.txt");
        assertThat(flat.getUrl("2024/a.txt", UrlArgs.defaults())).isEqualTo("2024/a.txt");
    }

    @Test
    void getUrlWithHttpServerShouldPrefixRelativePath() {
        LocalStorageClient withoutSlash =
                new LocalStorageClient(tempDir, "images", true, "https://cdn.example.com", true);
        LocalStorageClient withSlash =
                new LocalStorageClient(tempDir, "images", true, "https://cdn.example.com/", true);
        LocalStorageClient flat = new LocalStorageClient(tempDir, "images", false, "https://cdn.example.com", true);

        assertThat(withoutSlash.getUrl("2024/a.txt", UrlArgs.defaults()))
                .isEqualTo("https://cdn.example.com/images/2024/a.txt");
        assertThat(withSlash.getUrl("2024/a.txt", UrlArgs.defaults()))
                .isEqualTo("https://cdn.example.com/images/2024/a.txt");
        assertThat(flat.getUrl("a.txt", UrlArgs.defaults())).isEqualTo("https://cdn.example.com/a.txt");
    }

    @Test
    void pathTraversalOutsideBasePathShouldBeRejected() {
        LocalStorageClient appending = client();
        LocalStorageClient flat = new LocalStorageClient(tempDir, "images", false, "", true);

        assertThatThrownBy(() -> appending.save("../../escape.txt", stream("x"), SaveArgs.defaults()))
                .isInstanceOf(StorageOperationException.class)
                .hasMessageContaining("escapes");
        assertThatThrownBy(() -> appending.get("../../escape.txt")).isInstanceOf(StorageOperationException.class);
        assertThatThrownBy(() -> flat.save("../escape.txt", stream("x"), SaveArgs.defaults()))
                .isInstanceOf(StorageOperationException.class)
                .hasMessageContaining("escapes");
        assertThatThrownBy(() -> flat.exists("../escape.txt")).isInstanceOf(StorageOperationException.class);
    }

    @Test
    void missingBaseDirectoryShouldBeCreatedOnlyWhenEnabled() throws Exception {
        Path base = tempDir.resolve("created");
        new LocalStorageClient(base, "images", true, "", true);

        assertThat(base).exists();

        Path lazy = tempDir.resolve("lazy");
        LocalStorageClient client = new LocalStorageClient(lazy, "images", true, "", false);
        assertThat(lazy).doesNotExist();

        client.save("a.txt", stream("x"), SaveArgs.defaults());
        assertThat(lazy.resolve("images/a.txt")).exists();
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
                .property("httpServer", "https://cdn.example.com")
                .build());

        assertThat(container.getName()).isEqualTo("images");
        assertThat(container.getInfo().isDefault()).isTrue();
        assertThat(container.getUrl("a/b.txt", UrlArgs.defaults())).isEqualTo("https://cdn.example.com/images/a/b.txt");

        container.save("a/b.txt", stream("x"), SaveArgs.defaults());
        assertThat(tempDir.resolve("images/a/b.txt")).exists();
    }

    @Test
    void providerShouldSupportFlatLayoutWithoutHttpServer() {
        LocalStorageProvider provider = new LocalStorageProvider();

        var container = provider.createContainer(ContainerConfiguration.builder()
                .name("images")
                .type("local")
                .property("basePath", tempDir.toString())
                .property("appendContainerNameToBasePath", false)
                .build());

        container.save("a.txt", stream("x"), SaveArgs.defaults());

        assertThat(tempDir.resolve("a.txt")).exists();
        assertThat(container.getUrl("a.txt", UrlArgs.defaults())).isEqualTo("a.txt");
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
