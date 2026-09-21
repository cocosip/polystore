package io.github.cocosip.polystore.ks3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ksyun.ks3.dto.Bucket;
import com.ksyun.ks3.dto.GetObjectResult;
import com.ksyun.ks3.dto.Ks3Object;
import com.ksyun.ks3.dto.Ks3Result;
import com.ksyun.ks3.dto.ObjectMetadata;
import com.ksyun.ks3.dto.PutObjectResult;
import com.ksyun.ks3.exception.Ks3ServiceException;
import com.ksyun.ks3.service.Ks3Client;
import io.github.cocosip.polystore.SaveArgs;
import io.github.cocosip.polystore.UrlArgs;
import io.github.cocosip.polystore.exception.StorageFileNotFoundException;
import io.github.cocosip.polystore.exception.StorageOperationException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Ks3StorageClientTest {

    private static Ks3StorageClient client(RecordingKs3Client sdk, boolean createContainerIfNotExists) {
        return new Ks3StorageClient(sdk, "archive", 60, createContainerIfNotExists);
    }

    private static void save(Ks3StorageClient client, Map<String, String> metadata) {
        client.save(
                "2024/report.pdf",
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)),
                SaveArgs.builder()
                        .contentType("application/pdf")
                        .metadata(metadata)
                        .build());
    }

    @Test
    void saveShouldCreateTheBucketWhenAbsentAndThenUpload() {
        RecordingKs3Client sdk = new RecordingKs3Client();
        sdk.bucketExistsResult = false;

        save(client(sdk, true), Map.of());

        assertThat(sdk.calls).containsExactly("bucketExists", "createBucket", "putObject");
        assertThat(sdk.createdBucket).isEqualTo("archive");
        assertThat(sdk.uploadedBucket).isEqualTo("archive");
        assertThat(sdk.uploadedKey).isEqualTo("2024/report.pdf");
    }

    @Test
    void saveShouldNotCreateAnExistingBucket() {
        RecordingKs3Client sdk = new RecordingKs3Client();

        save(client(sdk, true), Map.of());

        assertThat(sdk.calls).containsExactly("bucketExists", "putObject");
        assertThat(sdk.createdBucket).isNull();
    }

    @Test
    void saveShouldSkipTheCheckWhenCreationIsDisabled() {
        RecordingKs3Client sdk = new RecordingKs3Client();

        save(client(sdk, false), Map.of());

        assertThat(sdk.calls).containsExactly("putObject");
    }

    @Test
    void saveShouldSendTheContentLengthContentTypeAndMetadata() {
        RecordingKs3Client sdk = new RecordingKs3Client();

        save(client(sdk, false), Map.of("source", "unit-test"));

        assertThat(sdk.uploadedMetadata.getContentLength()).isEqualTo(7L);
        assertThat(sdk.uploadedMetadata.getContentType()).isEqualTo("application/pdf");
        assertThat(sdk.uploadedMetadata.getUserMeta("source")).isEqualTo("unit-test");
    }

    @Test
    void saveShouldFailWhenTheBucketCannotBeChecked() {
        RecordingKs3Client sdk = new RecordingKs3Client();
        sdk.bucketExistsFailure = new Ks3ServiceException();

        assertThatThrownBy(() -> save(client(sdk, true), Map.of()))
                .isInstanceOf(StorageOperationException.class)
                .hasMessageContaining("Failed to ensure bucket");
        assertThat(sdk.calls).containsExactly("bucketExists");
    }

    @Test
    void getShouldMapAMissingObjectToFileNotFound() {
        RecordingKs3Client sdk = new RecordingKs3Client();
        sdk.getObjectFailure = serviceException(404, "NoSuchKey");

        assertThatThrownBy(() -> client(sdk, false).get("a.txt")).isInstanceOf(StorageFileNotFoundException.class);
    }

    @Test
    void getShouldWrapOtherServiceFailures() {
        RecordingKs3Client sdk = new RecordingKs3Client();
        sdk.getObjectFailure = serviceException(500, "InternalError");

        assertThatThrownBy(() -> client(sdk, false).get("a.txt"))
                .isInstanceOf(StorageOperationException.class)
                .hasMessageContaining("Failed to get file");
    }

    @Test
    void getShouldReturnTheObjectStream() {
        RecordingKs3Client sdk = new RecordingKs3Client();

        assertThat(client(sdk, false).get("a.txt")).isNotNull();
    }

    @Test
    void existsShouldFollowTheSdkResult() {
        RecordingKs3Client sdk = new RecordingKs3Client();
        assertThat(client(sdk, false).exists("a.txt")).isTrue();

        sdk.objectExistsResult = false;
        assertThat(client(sdk, false).exists("a.txt")).isFalse();
    }

    @Test
    void urlShouldUseTheContainerExpiryUnlessOverridden() {
        RecordingKs3Client sdk = new RecordingKs3Client();
        Ks3StorageClient client = client(sdk, false);

        client.getUrl("a.txt");
        assertThat(sdk.presignedExpiries).containsExactly(60);

        client.getUrl("a.txt", UrlArgs.builder().expiry(Duration.ofMinutes(5)).build());
        assertThat(sdk.presignedExpiries).containsExactly(60, 300);
    }

    @Test
    void deleteShouldCallTheSdk() {
        RecordingKs3Client sdk = new RecordingKs3Client();

        client(sdk, false).delete("a.txt");

        assertThat(sdk.calls).containsExactly("deleteObject");
        assertThat(sdk.deletedKey).isEqualTo("a.txt");
    }

    private static Ks3ServiceException serviceException(int statusCode, String errorCode) {
        Ks3ServiceException exception = new Ks3ServiceException();
        exception.setStatusCode(statusCode);
        exception.setErrorCode(errorCode);
        return exception;
    }

    /**
     * Records the SDK calls without touching the network: every method used by
     * {@link Ks3StorageClient} is overridden, the base client is never used.
     */
    private static final class RecordingKs3Client extends Ks3Client {

        private final List<String> calls = new ArrayList<>();
        private final List<Integer> presignedExpiries = new ArrayList<>();
        private boolean bucketExistsResult = true;
        private boolean objectExistsResult = true;
        private Ks3ServiceException bucketExistsFailure;
        private Ks3ServiceException getObjectFailure;
        private String createdBucket;
        private String uploadedBucket;
        private String uploadedKey;
        private ObjectMetadata uploadedMetadata;
        private String deletedKey;

        private RecordingKs3Client() {
            super("ak", "sk");
        }

        @Override
        public boolean bucketExists(String bucket) {
            calls.add("bucketExists");
            if (bucketExistsFailure != null) {
                throw bucketExistsFailure;
            }
            return bucketExistsResult;
        }

        @Override
        public Bucket createBucket(String bucket) {
            calls.add("createBucket");
            createdBucket = bucket;
            return null;
        }

        @Override
        public PutObjectResult putObject(String bucket, String key, InputStream input, ObjectMetadata metadata) {
            calls.add("putObject");
            uploadedBucket = bucket;
            uploadedKey = key;
            uploadedMetadata = metadata;
            return null;
        }

        @Override
        public GetObjectResult getObject(String bucket, String key) {
            calls.add("getObject");
            if (getObjectFailure != null) {
                throw getObjectFailure;
            }
            Ks3Object object = new Ks3Object();
            object.setObjectContent(new ByteArrayInputStream(new byte[0]));
            GetObjectResult result = new GetObjectResult();
            result.setObject(object);
            return result;
        }

        @Override
        public Ks3Result deleteObject(String bucket, String key) {
            calls.add("deleteObject");
            deletedKey = key;
            return null;
        }

        @Override
        public boolean objectExists(String bucket, String key) {
            calls.add("objectExists");
            return objectExistsResult;
        }

        @Override
        public String generatePresignedUrl(String bucket, String key, int expires) {
            calls.add("generatePresignedUrl");
            presignedExpiries.add(expires);
            return "http://" + bucket + ".ks3-cn-beijing.ksyuncs.com/" + key + "?Expires=" + expires + "&Signature=x";
        }
    }
}
