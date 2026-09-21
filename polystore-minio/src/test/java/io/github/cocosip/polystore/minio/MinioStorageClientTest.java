package io.github.cocosip.polystore.minio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.StorageProviderSaveArgs;
import io.github.cocosip.polystore.exception.StorageOperationException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MinioStorageClientTest {

    private static final long FIVE_MIB = 5L * 1024 * 1024;

    private static MinioStorageClient client(RecordingMinioClient sdk, boolean createBucketIfNotExists) {
        return new MinioStorageClient(sdk, "dicom", 60, createBucketIfNotExists);
    }

    private static ContainerConfiguration configuration(boolean multipart, long threshold, long partSize) {
        return ContainerConfiguration.builder()
                .name("dicom")
                .type("minio")
                .enableAutoMultiPartUpload(multipart)
                .multiPartUploadMinFileSize(threshold)
                .multiPartUploadShardingSize(partSize)
                .build();
    }

    private static StorageProviderSaveArgs saveArgs(
            ContainerConfiguration configuration, ByteArrayInputStream stream, long contentLength) {
        return new StorageProviderSaveArgs(
                "dicom",
                configuration,
                "2024/scan.dcm",
                stream,
                contentLength,
                ".dcm",
                true,
                "application/dicom",
                Map.of("source", "test"));
    }

    private static void save(MinioStorageClient client) {
        byte[] bytes = "x".getBytes(StandardCharsets.UTF_8);
        client.save(saveArgs(configuration(false, 10, 5), new ByteArrayInputStream(bytes), bytes.length));
    }

    @Test
    void saveShouldCreateTheBucketWhenAbsentAndThenUpload() {
        RecordingMinioClient sdk = new RecordingMinioClient();
        sdk.bucketExistsResult = false;

        save(client(sdk, true));

        assertThat(sdk.calls).containsExactly("bucketExists", "makeBucket", "putObject");
        assertThat(sdk.createdBucket).isEqualTo("dicom");
        assertThat(sdk.uploadedBucket).isEqualTo("dicom");
        assertThat(sdk.uploadedObject).isEqualTo("2024/scan.dcm");
        assertThat(sdk.uploadedBytes).containsExactly('x');
    }

    @Test
    void saveShouldNotCreateAnExistingBucket() {
        RecordingMinioClient sdk = new RecordingMinioClient();

        save(client(sdk, true));

        assertThat(sdk.calls).containsExactly("bucketExists", "putObject");
        assertThat(sdk.createdBucket).isNull();
    }

    @Test
    void saveShouldSkipTheBucketCheckWhenCreationIsDisabled() {
        RecordingMinioClient sdk = new RecordingMinioClient();

        save(client(sdk, false));

        assertThat(sdk.calls).containsExactly("putObject");
    }

    @Test
    void saveShouldUseConfiguredPartSizeOnlyAboveTheThreshold() {
        RecordingMinioClient sdk = new RecordingMinioClient();
        MinioStorageClient backend = client(sdk, false);
        ContainerConfiguration configuration = configuration(true, FIVE_MIB, FIVE_MIB);

        backend.save(saveArgs(configuration, new ByteArrayInputStream(new byte[(int) FIVE_MIB]), FIVE_MIB));
        assertThat(sdk.objectSize).isEqualTo(FIVE_MIB);
        assertThat(sdk.partSize).isEqualTo(FIVE_MIB);
        assertThat(sdk.partCount).isEqualTo(1);

        sdk.resetUpload();
        backend.save(saveArgs(configuration, new ByteArrayInputStream(new byte[(int) FIVE_MIB + 1]), FIVE_MIB + 1));
        assertThat(sdk.objectSize).isEqualTo(FIVE_MIB + 1);
        assertThat(sdk.partSize).isEqualTo(FIVE_MIB);
        assertThat(sdk.partCount).isEqualTo(2);
    }

    @Test
    void saveShouldConsumeExactlyTheDeclaredLengthWithoutClosingTheCallerStream() {
        RecordingMinioClient sdk = new RecordingMinioClient();
        TrackingInputStream stream = new TrackingInputStream("abc-extra".getBytes(StandardCharsets.UTF_8));

        String fileId = client(sdk, false).save(saveArgs(configuration(false, 10, 5), stream, 3));

        assertThat(fileId).isEqualTo("2024/scan.dcm");
        assertThat(sdk.uploadedBytes).asString(StandardCharsets.UTF_8).isEqualTo("abc");
        assertThat(stream.read()).isEqualTo('-');
        assertThat(stream.closed).isFalse();
    }

    @Test
    void saveShouldFailOnEarlyEofWithoutClosingTheCallerStream() {
        RecordingMinioClient sdk = new RecordingMinioClient();
        TrackingInputStream stream = new TrackingInputStream(new byte[] {'x'});

        assertThatThrownBy(() -> client(sdk, false).save(saveArgs(configuration(false, 10, 5), stream, 2)))
                .isInstanceOf(StorageOperationException.class)
                .hasRootCauseInstanceOf(java.io.EOFException.class);
        assertThat(stream.closed).isFalse();
    }

    @Test
    void saveShouldFailWhenTheBucketCannotBeChecked() {
        RecordingMinioClient sdk = new RecordingMinioClient();
        sdk.bucketExistsFailure = new IllegalStateException("unreachable");

        assertThatThrownBy(() -> save(client(sdk, true)))
                .isInstanceOf(StorageOperationException.class)
                .hasMessageContaining("Failed to ensure bucket");
        assertThat(sdk.calls).containsExactly("bucketExists");
    }

    @Test
    void saveShouldFailWhenTheBucketCannotBeCreated() {
        RecordingMinioClient sdk = new RecordingMinioClient();
        sdk.bucketExistsResult = false;
        sdk.makeBucketFailure = new IllegalStateException("denied");

        assertThatThrownBy(() -> save(client(sdk, true)))
                .isInstanceOf(StorageOperationException.class)
                .hasMessageContaining("Failed to ensure bucket");
        assertThat(sdk.calls).containsExactly("bucketExists", "makeBucket");
    }

    /** Records MinIO SDK calls without touching the network. */
    private static final class RecordingMinioClient extends MinioClient {

        private final List<String> calls = new ArrayList<>();
        private boolean bucketExistsResult = true;
        private RuntimeException bucketExistsFailure;
        private RuntimeException makeBucketFailure;
        private String createdBucket;
        private String uploadedBucket;
        private String uploadedObject;
        private byte[] uploadedBytes;
        private long objectSize;
        private long partSize;
        private int partCount;

        private RecordingMinioClient() {
            super(MinioClient.builder()
                    .endpoint("http://127.0.0.1:1")
                    .credentials("ak", "sk")
                    .region("us-east-1")
                    .build());
        }

        @Override
        public boolean bucketExists(BucketExistsArgs args) {
            calls.add("bucketExists");
            if (bucketExistsFailure != null) {
                throw bucketExistsFailure;
            }
            return bucketExistsResult;
        }

        @Override
        public void makeBucket(MakeBucketArgs args) {
            calls.add("makeBucket");
            createdBucket = args.bucket();
            if (makeBucketFailure != null) {
                throw makeBucketFailure;
            }
        }

        @Override
        public ObjectWriteResponse putObject(PutObjectArgs args) throws IOException {
            calls.add("putObject");
            uploadedBucket = args.bucket();
            uploadedObject = args.object();
            objectSize = args.objectSize();
            partSize = args.partSize();
            partCount = args.partCount();
            uploadedBytes = args.stream().readAllBytes();
            return null;
        }

        private void resetUpload() {
            calls.clear();
            uploadedBytes = null;
            objectSize = 0;
            partSize = 0;
            partCount = 0;
        }
    }

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
