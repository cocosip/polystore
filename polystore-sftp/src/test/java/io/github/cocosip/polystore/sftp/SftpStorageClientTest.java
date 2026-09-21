package io.github.cocosip.polystore.sftp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jcraft.jsch.ChannelSftp;
import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.SaveArgs;
import io.github.cocosip.polystore.exception.StorageFileAlreadyExistsException;
import io.github.cocosip.polystore.exception.StorageFileNotFoundException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SftpStorageClientTest {

    /** In-memory fake of the remote filesystem, recording leased-channel lifecycle. */
    private static final class FakeChannelFactory implements SftpChannelFactory {

        final Map<String, byte[]> files = new HashMap<>();
        final List<Integer> borrowCounts = new ArrayList<>();

        @Override
        public PooledSftpChannel create(
                String host,
                int port,
                String username,
                String password,
                String privateKeyPath,
                String strictHostKeyChecking) {
            borrowCounts.add(1);
            return new PooledSftpChannel(new FakeChannel(), null);
        }

        final class FakeChannel extends ChannelSftp {

            @Override
            public void put(java.io.InputStream src, String dst, int mode) throws com.jcraft.jsch.SftpException {
                try {
                    files.put(dst, src.readAllBytes());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public java.io.InputStream get(String src) throws com.jcraft.jsch.SftpException {
                byte[] content = files.get(src);
                if (content == null) {
                    throw new com.jcraft.jsch.SftpException(SSH_FX_NO_SUCH_FILE, src);
                }
                return new ByteArrayInputStream(content);
            }

            @Override
            public void rm(String path) throws com.jcraft.jsch.SftpException {
                if (files.remove(path) == null) {
                    throw new com.jcraft.jsch.SftpException(SSH_FX_NO_SUCH_FILE, path);
                }
            }

            @Override
            public com.jcraft.jsch.SftpATTRS stat(String path) throws com.jcraft.jsch.SftpException {
                if (!files.containsKey(path)) {
                    throw new com.jcraft.jsch.SftpException(SSH_FX_NO_SUCH_FILE, path);
                }
                return null; // exists() only inspects the exception, never the attrs
            }
        }
    }

    private SftpStorageClient client(FakeChannelFactory factory) {
        return new SftpStorageClient(
                new SftpConnectionPool(factory, "sftp.internal", 22, "user", "pw", null, "no", 2),
                "/data/files",
                "https://files.example.com");
    }

    private static ByteArrayInputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void saveGetExistsDeleteShouldWorkAgainstFakeChannel() throws Exception {
        FakeChannelFactory factory = new FakeChannelFactory();
        SftpStorageClient client = client(factory);

        client.save("2024/a.txt", stream("hello"), SaveArgs.defaults());

        assertThat(factory.files).containsKey("/data/files/2024/a.txt");
        assertThat(client.exists("2024/a.txt")).isTrue();
        try (var in = client.get("2024/a.txt")) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
        }

        client.delete("2024/a.txt");
        assertThat(client.exists("2024/a.txt")).isFalse();
        client.delete("2024/a.txt"); // missing files are silently ignored
    }

    @Test
    void overwriteFalseShouldRejectExistingFiles() {
        FakeChannelFactory factory = new FakeChannelFactory();
        SftpStorageClient client = client(factory);
        client.save("a.txt", stream("v1"), SaveArgs.defaults());

        assertThatThrownBy(() -> client.save(
                        "a.txt",
                        stream("v2"),
                        SaveArgs.builder().overwrite(false).build()))
                .isInstanceOf(StorageFileAlreadyExistsException.class);
    }

    @Test
    void getMissingFileShouldThrowNotFound() {
        assertThatThrownBy(() -> client(new FakeChannelFactory()).get("nope.txt"))
                .isInstanceOf(StorageFileNotFoundException.class);
    }

    @Test
    void channelsShouldBeReleasedBackToThePool() {
        FakeChannelFactory factory = new FakeChannelFactory();
        SftpStorageClient client = client(factory);

        for (int i = 0; i < 5; i++) {
            client.save("f" + i + ".txt", stream("x"), SaveArgs.defaults());
        }

        // pool size 2: no more than 2 channels are ever created for sequential operations
        assertThat(factory.borrowCounts.size()).isLessThanOrEqualTo(2);
    }

    @Test
    void getUrlShouldComposePrefixAndFileName() {
        assertThat(client(new FakeChannelFactory()).getUrl("a/b.txt")).isEqualTo("https://files.example.com/a/b.txt");
        assertThat(new SftpStorageClient(
                                new SftpConnectionPool(new FakeChannelFactory(), "h", 22, "u", "p", null, "no", 1),
                                "/data",
                                "")
                        .getUrl("a.txt"))
                .isEqualTo("a.txt");
    }

    @Test
    void providerShouldRequireHostUsernameBasePathAndCredentials() {
        SftpStorageProvider provider = new SftpStorageProvider();

        assertThat(provider.getType()).isEqualTo("sftp");
        assertThatThrownBy(() -> provider.createContainer(ContainerConfiguration.builder()
                        .name("c")
                        .type("sftp")
                        .properties(Map.of("username", "u", "basePath", "/data", "password", "p"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("host");
        assertThatThrownBy(() -> provider.createContainer(ContainerConfiguration.builder()
                        .name("c")
                        .type("sftp")
                        .properties(Map.of("host", "h", "basePath", "/data"))
                        .build()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> provider.createContainer(ContainerConfiguration.builder()
                        .name("c")
                        .type("sftp")
                        .properties(Map.of("host", "h", "username", "u", "basePath", "/data"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("password");
    }
}
