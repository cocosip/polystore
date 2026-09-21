package io.github.cocosip.polystore;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultStorageContainerTest {

    private static final class RecordingClient implements StorageClient {

        final Map<String, String> saved = new HashMap<>();
        final Map<String, String> urls = new HashMap<>();
        boolean deleted;

        @Override
        public void save(String fileName, InputStream inputStream, SaveArgs args) {
            saved.put(fileName, args.getContentType());
        }

        @Override
        public InputStream get(String fileName) {
            return new ByteArrayInputStream(fileName.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void delete(String fileName) {
            deleted = true;
        }

        @Override
        public boolean exists(String fileName) {
            return saved.containsKey(fileName);
        }

        @Override
        public String getUrl(String fileName, UrlArgs args) {
            return urls.computeIfAbsent(fileName, f -> "mem://" + f);
        }

        @Override
        public void deleteAll(java.util.Collection<String> fileNames) {
            deleted = true;
        }
    }

    @Test
    void shouldExposeIdentityAndDelegateEveryOperation() throws IOException {
        RecordingClient client = new RecordingClient();
        DefaultStorageContainer container = new DefaultStorageContainer("images", "local", client);

        assertThat(container.getName()).isEqualTo("images");
        assertThat(container.getProviderType()).isEqualTo("local");
        assertThat(container.getInfo().getName()).isEqualTo("images");
        assertThat(container.getInfo().getProviderType()).isEqualTo("local");
        assertThat(container.getInfo().isDefault()).isFalse();

        container.save(
                "a.txt",
                new ByteArrayInputStream(new byte[0]),
                SaveArgs.builder().contentType("text/plain").build());
        assertThat(client.saved).containsEntry("a.txt", "text/plain");

        try (InputStream in = container.get("a.txt")) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("a.txt");
        }
        assertThat(container.exists("a.txt")).isTrue();
        assertThat(container.getUrl("a.txt", UrlArgs.defaults())).isEqualTo("mem://a.txt");
        container.deleteAll(List.of("a.txt"));
        assertThat(client.deleted).isTrue();
    }

    @Test
    void explicitDefaultFlagShouldBeExposedThroughInfo() {
        DefaultStorageContainer container = new DefaultStorageContainer("dicom", "minio", true, new RecordingClient());

        assertThat(container.getInfo().isDefault()).isTrue();
    }

    @Test
    void explicitInfoShouldBeExposedAsIs() {
        ContainerInfo info = new ContainerInfo("c", "s3", false);
        DefaultStorageContainer container = new DefaultStorageContainer("c", "s3", info, new RecordingClient());

        assertThat(container.getInfo()).isSameAs(info);
    }

    @Test
    void fromShouldDeriveIdentityFromConfiguration() {
        ContainerConfiguration config = ContainerConfiguration.builder()
                .name("backup")
                .type("s3")
                .isDefault(true)
                .build();
        RecordingClient client = new RecordingClient();

        DefaultStorageContainer container = DefaultStorageContainer.from(config, client);

        assertThat(container.getName()).isEqualTo("backup");
        assertThat(container.getProviderType()).isEqualTo("s3");
        assertThat(container.getInfo().isDefault()).isTrue();
    }
}
