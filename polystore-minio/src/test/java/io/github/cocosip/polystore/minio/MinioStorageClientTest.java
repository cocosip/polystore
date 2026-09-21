package io.github.cocosip.polystore.minio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cocosip.polystore.SaveArgs;
import io.github.cocosip.polystore.exception.StorageOperationException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MinioStorageClientTest {

    private static MinioStorageClient client(RecordingMinioClient sdk, boolean createBucketIfNotExists) {
        return new MinioStorageClient(sdk, "dicom", 60, createBucketIfNotExists);
    }

    private static void save(MinioStorageClient client) {
        client.save(
                "2024/scan.dcm", new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)), SaveArgs.defaults());
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

    /**
     * Records the SDK calls of the lazy bucket creation without touching the network: the methods
     * used by {@link MinioStorageClient} are overridden, the base client is never used.
     */
    private static final class RecordingMinioClient extends MinioClient {

        private final List<String> calls = new ArrayList<>();
        private boolean bucketExistsResult = true;
        private RuntimeException bucketExistsFailure;
        private RuntimeException makeBucketFailure;
        private String createdBucket;
        private String uploadedBucket;
        private String uploadedObject;

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
        public ObjectWriteResponse putObject(PutObjectArgs args) {
            calls.add("putObject");
            uploadedBucket = args.bucket();
            uploadedObject = args.object();
            return null;
        }
    }
}
