package io.github.cocosip.polystore.sftp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jcraft.jsch.ChannelSftp;
import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.StorageProviderAccessArgs;
import io.github.cocosip.polystore.StorageProviderDeleteArgs;
import io.github.cocosip.polystore.StorageProviderExistsArgs;
import io.github.cocosip.polystore.StorageProviderGetArgs;
import io.github.cocosip.polystore.StorageProviderSaveArgs;
import io.github.cocosip.polystore.exception.StorageFileAlreadyExistsException;
import io.github.cocosip.polystore.exception.StorageOperationException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class SftpStorageClientTest {
    private static final ContainerConfiguration CONFIG =
            ContainerConfiguration.builder().name("files").type("sftp").build();

    private static final class FakeChannelFactory implements SftpChannelFactory {
        final Map<String, byte[]> files = new HashMap<>();
        final List<Integer> created = new ArrayList<>();
        final List<FakeChannel> channels = new ArrayList<>();
        final List<PooledSftpChannel> pooled = new ArrayList<>();

        @Override
        public PooledSftpChannel create(
                String host,
                int port,
                String username,
                String password,
                String privateKeyPath,
                String strictHostKeyChecking) {
            created.add(1);
            FakeChannel channel = new FakeChannel();
            channels.add(channel);
            PooledSftpChannel pooledChannel = new PooledSftpChannel(channel, null);
            pooled.add(pooledChannel);
            return pooledChannel;
        }

        final class FakeChannel extends ChannelSftp {
            volatile boolean alive = true;

            @Override
            public boolean isConnected() {
                return alive;
            }

            @Override
            public void put(java.io.InputStream src, String dst, int mode) {
                try {
                    files.put(dst, src.readAllBytes());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public java.io.InputStream get(String src) throws com.jcraft.jsch.SftpException {
                byte[] content = files.get(src);
                if (content == null) throw new com.jcraft.jsch.SftpException(SSH_FX_NO_SUCH_FILE, src);
                return new ByteArrayInputStream(content);
            }

            @Override
            public void rm(String path) throws com.jcraft.jsch.SftpException {
                if (files.remove(path) == null) throw new com.jcraft.jsch.SftpException(SSH_FX_NO_SUCH_FILE, path);
            }

            @Override
            public com.jcraft.jsch.SftpATTRS stat(String path) throws com.jcraft.jsch.SftpException {
                if (!files.containsKey(path)) throw new com.jcraft.jsch.SftpException(SSH_FX_NO_SUCH_FILE, path);
                return null;
            }
        }
    }

    private SftpConnectionPool pool(FakeChannelFactory factory, int poolSize) {
        return new SftpConnectionPool(factory, "sftp.internal", 22, "user", "pw", null, "no", poolSize);
    }

    private SftpStorageClient backend(FakeChannelFactory factory) {
        return new SftpStorageClient(pool(factory, 2), "/data/files", "https://files.example.com");
    }

    private StorageProviderSaveArgs saveArgs(String id, String text, long length, boolean override) {
        return new StorageProviderSaveArgs(
                "files",
                CONFIG,
                id,
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),
                length,
                ".txt",
                override,
                "text/plain",
                Map.of());
    }

    @Test
    void operationsShouldWorkAgainstFakeChannel() throws Exception {
        FakeChannelFactory factory = new FakeChannelFactory();
        SftpStorageClient backend = backend(factory);
        assertThat(backend.save(saveArgs("2026/a.txt", "hello-extra", 5, false)))
                .isEqualTo("2026/a.txt");
        assertThat(factory.files.get("/data/files/2026/a.txt"))
                .asString(StandardCharsets.UTF_8)
                .isEqualTo("hello");
        assertThat(backend.exists(new StorageProviderExistsArgs("files", CONFIG, "2026/a.txt")))
                .isTrue();
        try (var in = backend.getOrNull(new StorageProviderGetArgs("files", CONFIG, "2026/a.txt"))) {
            assertThat(in.readAllBytes()).asString(StandardCharsets.UTF_8).isEqualTo("hello");
        }
        assertThat(backend.delete(new StorageProviderDeleteArgs("files", CONFIG, "2026/a.txt")))
                .isTrue();
        assertThat(backend.delete(new StorageProviderDeleteArgs("files", CONFIG, "2026/a.txt")))
                .isFalse();
    }

    @Test
    void overwriteAndEarlyEofShouldBeEnforced() {
        FakeChannelFactory factory = new FakeChannelFactory();
        SftpStorageClient backend = backend(factory);
        backend.save(saveArgs("a.txt", "v1", 2, false));
        assertThatThrownBy(() -> backend.save(saveArgs("a.txt", "v2", 2, false)))
                .isInstanceOf(StorageFileAlreadyExistsException.class);
        assertThatThrownBy(() -> backend.save(saveArgs("short.txt", "x", 2, false)))
                .isInstanceOf(StorageOperationException.class)
                .hasRootCauseInstanceOf(java.io.EOFException.class);
    }

    @Test
    void pathSegmentsEscapingTheBasePathShouldBeRejected() {
        FakeChannelFactory factory = new FakeChannelFactory();
        SftpStorageClient backend = backend(factory);

        assertThatThrownBy(() -> backend.save(saveArgs("../escape.txt", "x", 1, true)))
                .isInstanceOf(StorageOperationException.class)
                .hasMessageContaining("escapes the base path");
        assertThatThrownBy(() -> backend.exists(new StorageProviderExistsArgs("files", CONFIG, "a/../../b.txt")))
                .isInstanceOf(StorageOperationException.class)
                .hasMessageContaining("escapes the base path");
        assertThat(factory.files).isEmpty();
    }

    @Test
    void accessUrlAndPoolReuseShouldWork() {
        FakeChannelFactory factory = new FakeChannelFactory();
        SftpStorageClient backend = backend(factory);
        for (int i = 0; i < 5; i++) backend.save(saveArgs("f" + i + ".txt", "x", 1, false));
        assertThat(factory.created).hasSizeLessThanOrEqualTo(2);
        assertThat(backend.getAccessUrl(new StorageProviderAccessArgs(
                        "files", CONFIG, "a.txt", Instant.parse("2026-09-22T00:00:00Z"), false)))
                .isEqualTo("https://files.example.com/a.txt");
    }

    @Test
    void exhaustedPoolShouldBlockUntilAChannelIsReleased() throws Exception {
        FakeChannelFactory factory = new FakeChannelFactory();
        SftpConnectionPool pool = pool(factory, 1);
        SftpChannelFactory.PooledSftpChannel first = pool.borrow();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<SftpChannelFactory.PooledSftpChannel> second = executor.submit(pool::borrow);
            // the pool is at capacity: the second borrow must still be blocked
            assertThrows(TimeoutException.class, () -> second.get(100, TimeUnit.MILLISECONDS));
            pool.release(first);
            assertThat(second.get(2, TimeUnit.SECONDS)).isSameAs(first);
            assertThat(factory.created).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void deadChannelsShouldBeEvictedInsteadOfReused() throws Exception {
        FakeChannelFactory factory = new FakeChannelFactory();
        SftpConnectionPool pool = pool(factory, 1);
        SftpChannelFactory.PooledSftpChannel first = pool.borrow();
        factory.channels.get(0).alive = false;

        pool.release(first);

        SftpChannelFactory.PooledSftpChannel second = pool.borrow();
        assertThat(second).isNotSameAs(first);
        assertThat(factory.created).hasSize(2);
    }

    @Test
    void openStreamShouldHoldTheLeaseUntilClosed() throws Exception {
        FakeChannelFactory factory = new FakeChannelFactory();
        SftpConnectionPool pool = pool(factory, 1);
        SftpStorageClient backend = new SftpStorageClient(pool, "/data/files", "");
        backend.save(saveArgs("a.txt", "hello", 5, false));

        InputStream stream = backend.getOrNull(new StorageProviderGetArgs("files", CONFIG, "a.txt"));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            assertThat(stream.readAllBytes()).asString(StandardCharsets.UTF_8).isEqualTo("hello");
            // the pooled channel is still leased by the open stream
            Future<SftpChannelFactory.PooledSftpChannel> concurrent = executor.submit(pool::borrow);
            assertThrows(TimeoutException.class, () -> concurrent.get(100, TimeUnit.MILLISECONDS));

            stream.close();

            assertThat(concurrent.get(2, TimeUnit.SECONDS)).isSameAs(factory.pooled.get(0));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void providerShouldValidateRequiredConfiguration() {
        SftpStorageProvider provider = new SftpStorageProvider();
        assertThatThrownBy(() -> provider.createBackend(ContainerConfiguration.builder()
                        .name("c")
                        .type("sftp")
                        .properties(Map.of("username", "u", "basePath", "/data", "password", "p"))
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("host");
    }
}
