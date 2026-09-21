package io.github.cocosip.polystore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class StorageClientConvenienceTest {
    private final Path tempDir = Path.of("target", "storage-client-convenience-test");

    private static class RecordingClient implements StorageClient {
        String fileId;
        InputStream stream;
        long contentLength;
        String ext;
        boolean overrideExisting;
        InputStream returnedStream = new ByteArrayInputStream("result".getBytes(StandardCharsets.UTF_8));

        @Override
        public String save(
                String fileId,
                InputStream stream,
                long contentLength,
                String ext,
                boolean overrideExisting,
                StorageSaveOptions options) {
            this.fileId = fileId;
            this.stream = stream;
            this.contentLength = contentLength;
            this.ext = ext;
            this.overrideExisting = overrideExisting;
            return fileId;
        }

        @Override
        public boolean delete(String fileId) {
            return false;
        }

        @Override
        public boolean exists(String fileId) {
            return false;
        }

        @Override
        public boolean download(String fileId, Path path) {
            return false;
        }

        @Override
        public InputStream getOrNull(String fileId) {
            return returnedStream;
        }

        @Override
        public String getAccessUrl(String fileId, Instant expires, boolean checkFileExist) {
            return "";
        }
    }

    @Test
    void byteArraySaveShouldSupplyLengthAndDefaultOverwriteToFalse() {
        RecordingClient client = new RecordingClient();
        assertThat(client.save("id", new byte[] {1, 2, 3}, ".bin")).isEqualTo("id");
        assertThat(client.contentLength).isEqualTo(3);
        assertThat(client.ext).isEqualTo(".bin");
        assertThat(client.overrideExisting).isFalse();
    }

    @Test
    void pathSaveShouldUseFileSizeAndFinalExtensionThenCloseCreatedStream() throws IOException {
        RecordingClient client = new RecordingClient();
        Files.createDirectories(tempDir);
        Path path = tempDir.resolve("archive.part.tar");
        Files.writeString(path, "hello", StandardCharsets.UTF_8);
        assertThat(client.save("logical", path, true)).isEqualTo("logical");
        assertThat(client.contentLength).isEqualTo(5);
        assertThat(client.ext).isEqualTo(".tar");
        assertThat(client.overrideExisting).isTrue();
        assertThatThrownBy(() -> client.stream.read()).isInstanceOf(IOException.class);
    }

    @Test
    void pathSaveShouldRejectAPathWithoutExtension() throws IOException {
        RecordingClient client = new RecordingClient();
        Files.createDirectories(tempDir);
        Path path = tempDir.resolve("README");
        Files.writeString(path, "hello", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> client.save("logical", path))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("extension");
    }

    @Test
    void getAllBytesShouldCloseReturnedStream() {
        RecordingClient client = new RecordingClient();
        CloseRecordingInputStream stream = new CloseRecordingInputStream("content".getBytes(StandardCharsets.UTF_8));
        client.returnedStream = stream;
        assertThat(client.getAllBytes("id")).asString(StandardCharsets.UTF_8).isEqualTo("content");
        assertThat(stream.closed).isTrue();
    }

    @Test
    void getAllBytesOrNullShouldPreserveMissingValue() {
        RecordingClient client = new RecordingClient();
        client.returnedStream = null;
        assertThat(client.getAllBytesOrNull("missing")).isNull();
    }

    private static final class CloseRecordingInputStream extends ByteArrayInputStream {
        boolean closed;

        CloseRecordingInputStream(byte[] content) {
            super(content);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
