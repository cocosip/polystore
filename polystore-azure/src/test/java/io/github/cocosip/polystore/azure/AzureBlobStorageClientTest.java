package io.github.cocosip.polystore.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.StorageProviderSaveArgs;
import io.github.cocosip.polystore.exception.StorageOperationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AzureBlobStorageClientTest {
    private static final long FIVE_MIB = 5L * 1024 * 1024;

    @Test
    void saveShouldConfigureMultipartOnlyAboveTheEnabledThreshold() {
        CapturingBackend capturing = backend();
        ContainerConfiguration disabled = configuration(false);
        capturing.backend.save(args(disabled, new byte[] {'x'}, 1));
        assertThat(capturing.options.get().getParallelTransferOptions()).isNull();

        ContainerConfiguration enabled = configuration(true);
        capturing.backend.save(args(enabled, new byte[(int) FIVE_MIB], FIVE_MIB));
        assertThat(capturing.options.get().getParallelTransferOptions()).isNull();

        capturing.backend.save(args(enabled, new byte[(int) FIVE_MIB + 1], FIVE_MIB + 1));
        assertThat(capturing.options.get().getParallelTransferOptions().getMaxSingleUploadSizeLong())
                .isEqualTo(FIVE_MIB);
        assertThat(capturing.options.get().getParallelTransferOptions().getBlockSizeLong())
                .isEqualTo(FIVE_MIB);
    }

    @Test
    void saveShouldConsumeExactlyTheDeclaredLengthAndKeepCallerStreamOpen() throws Exception {
        CapturingBackend capturing = backend();
        TrackingInputStream stream = new TrackingInputStream("abc-extra".getBytes(StandardCharsets.UTF_8));

        assertThat(capturing.backend.save(args(configuration(false), stream, 3)))
                .isEqualTo("scan.dcm");
        assertThat(capturing.uploaded.get()).asString(StandardCharsets.UTF_8).isEqualTo("abc");
        assertThat(stream.read()).isEqualTo('-');
        assertThat(stream.closed).isFalse();
    }

    @Test
    void saveShouldRejectEarlyEofAndKeepCallerStreamOpen() {
        CapturingBackend capturing = backend();
        TrackingInputStream stream = new TrackingInputStream(new byte[] {'x'});

        assertThatThrownBy(() -> capturing.backend.save(args(configuration(false), stream, 2)))
                .isInstanceOf(StorageOperationException.class)
                .hasRootCauseInstanceOf(java.io.EOFException.class);
        assertThat(stream.closed).isFalse();
    }

    @Test
    void saveShouldCreateTheContainerLazily() {
        BlobContainerClient container = mock(BlobContainerClient.class);
        BlobClient blob = mock(BlobClient.class);
        when(container.getBlobClient("scan.dcm")).thenReturn(blob);
        when(container.exists()).thenReturn(false);
        doAnswer(invocation -> {
                    BlobParallelUploadOptions options = invocation.getArgument(0);
                    options.getDataStream().readAllBytes();
                    return null;
                })
                .when(blob)
                .uploadWithResponse(any(BlobParallelUploadOptions.class), isNull(), isNull());

        new AzureBlobStorageClient(container, true).save(args(configuration(false), new byte[] {'x'}, 1));

        verify(container).create();
    }

    private static CapturingBackend backend() {
        BlobContainerClient container = mock(BlobContainerClient.class);
        BlobClient blob = mock(BlobClient.class);
        when(container.getBlobClient("scan.dcm")).thenReturn(blob);
        AtomicReference<BlobParallelUploadOptions> options = new AtomicReference<>();
        AtomicReference<byte[]> uploaded = new AtomicReference<>();
        doAnswer(invocation -> {
                    BlobParallelUploadOptions value = invocation.getArgument(0);
                    options.set(value);
                    uploaded.set(value.getDataStream().readAllBytes());
                    return null;
                })
                .when(blob)
                .uploadWithResponse(any(BlobParallelUploadOptions.class), isNull(), isNull());
        return new CapturingBackend(new AzureBlobStorageClient(container, false), options, uploaded);
    }

    private static ContainerConfiguration configuration(boolean multipart) {
        return ContainerConfiguration.builder()
                .name("blobs")
                .type("azure")
                .enableAutoMultiPartUpload(multipart)
                .multiPartUploadMinFileSize(FIVE_MIB)
                .multiPartUploadShardingSize(FIVE_MIB)
                .build();
    }

    private static StorageProviderSaveArgs args(ContainerConfiguration configuration, byte[] bytes, long length) {
        return args(configuration, new ByteArrayInputStream(bytes), length);
    }

    private static StorageProviderSaveArgs args(
            ContainerConfiguration configuration, ByteArrayInputStream stream, long length) {
        return new StorageProviderSaveArgs(
                "blobs",
                configuration,
                "scan.dcm",
                stream,
                length,
                ".dcm",
                true,
                "application/dicom",
                Map.of("source", "test"));
    }

    private record CapturingBackend(
            AzureBlobStorageClient backend,
            AtomicReference<BlobParallelUploadOptions> options,
            AtomicReference<byte[]> uploaded) {}

    private static final class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
