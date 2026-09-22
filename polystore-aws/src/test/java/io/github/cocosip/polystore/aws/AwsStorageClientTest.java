package io.github.cocosip.polystore.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.StorageProviderSaveArgs;
import io.github.cocosip.polystore.exception.StorageOperationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class AwsStorageClientTest {
    private static final long FIVE_MIB = 5L * 1024 * 1024;

    @Test
    void singleUploadShouldStreamExactlyTheDeclaredLengthAtTheThreshold() throws Exception {
        RecordingS3Client recording = new RecordingS3Client();
        byte[] bytes = new byte[(int) FIVE_MIB + 1];
        bytes[(int) FIVE_MIB] = 7;
        TrackingInputStream stream = new TrackingInputStream(bytes);

        assertThat(client(recording, false).save(args(configuration(true), stream, FIVE_MIB, FIVE_MIB)))
                .isEqualTo("a.txt");

        assertThat(recording.calls).containsExactly("putObject");
        assertThat(recording.uploadedBodies.getFirst()).hasSize((int) FIVE_MIB);
        assertThat(stream.read()).isEqualTo(7);
        assertThat(stream.closed).isFalse();
    }

    @Test
    void multipartShouldUploadOrderedPartsAndShortFinalPart() {
        RecordingS3Client recording = new RecordingS3Client();
        byte[] bytes = new byte[(int) FIVE_MIB + 3];
        bytes[(int) FIVE_MIB] = 7;
        bytes[(int) FIVE_MIB + 1] = 8;
        bytes[(int) FIVE_MIB + 2] = 9;

        client(recording, false)
                .save(args(configuration(true), new TrackingInputStream(bytes), bytes.length, FIVE_MIB));

        assertThat(recording.calls)
                .containsExactly("createMultipartUpload", "uploadPart", "uploadPart", "completeMultipartUpload");
        assertThat(recording.partNumbers).containsExactly(1, 2);
        assertThat(recording.partLengths).containsExactly(FIVE_MIB, 3L);
        assertThat(recording.completedPartNumbers).containsExactly(1, 2);
        assertThat(recording.completedEtags).containsExactly("etag-1", "etag-2");
    }

    @Test
    void multipartFailureShouldAbortAndPreserveAbortFailureAsSuppressed() {
        RecordingS3Client recording = new RecordingS3Client();
        IllegalStateException uploadFailure = new IllegalStateException("part failed");
        IllegalStateException abortFailure = new IllegalStateException("abort failed");
        recording.uploadPartFailureAt = 2;
        recording.uploadPartFailure = uploadFailure;
        recording.abortFailure = abortFailure;
        byte[] bytes = new byte[(int) FIVE_MIB + 1];

        assertThatThrownBy(() -> client(recording, false)
                        .save(args(configuration(true), new TrackingInputStream(bytes), bytes.length, FIVE_MIB)))
                .isInstanceOf(StorageOperationException.class)
                .hasRootCause(uploadFailure)
                .satisfies(error -> assertThat(error.getCause().getSuppressed()).containsExactly(abortFailure));
        assertThat(recording.calls)
                .containsExactly("createMultipartUpload", "uploadPart", "uploadPart", "abortMultipartUpload");
    }

    @Test
    void completionFailureShouldAbort() {
        RecordingS3Client recording = new RecordingS3Client();
        recording.completeFailure = new IllegalStateException("complete failed");
        byte[] bytes = new byte[(int) FIVE_MIB + 1];

        assertThatThrownBy(() -> client(recording, false)
                        .save(args(configuration(true), new TrackingInputStream(bytes), bytes.length, FIVE_MIB)))
                .isInstanceOf(StorageOperationException.class)
                .hasRootCauseMessage("complete failed");
        assertThat(recording.calls).endsWith("completeMultipartUpload", "abortMultipartUpload");
    }

    @Test
    void earlyEofShouldFailAndAbortMultipartWithoutClosingCallerStream() {
        RecordingS3Client recording = new RecordingS3Client();
        TrackingInputStream stream = new TrackingInputStream(new byte[(int) FIVE_MIB]);

        assertThatThrownBy(
                        () -> client(recording, false).save(args(configuration(true), stream, FIVE_MIB + 1, FIVE_MIB)))
                .isInstanceOf(StorageOperationException.class)
                .hasRootCauseInstanceOf(java.io.EOFException.class);
        assertThat(recording.calls).endsWith("abortMultipartUpload");
        assertThat(stream.closed).isFalse();
    }

    @Test
    void saveShouldCreateTheBucketLazily() {
        RecordingS3Client recording = new RecordingS3Client();
        recording.headBucketFailure = NoSuchBucketException.builder().build();

        client(recording, true).save(args(configuration(false), new TrackingInputStream(new byte[] {'x'}), 1, 10));

        assertThat(recording.calls).containsExactly("headBucket", "createBucket", "putObject");
        assertThat(recording.buckets).containsExactly("my-bucket", "my-bucket");
    }

    private static AwsStorageClient client(RecordingS3Client recording, boolean createContainerIfNotExists) {
        S3Presigner presigner = S3Presigner.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("ak", "sk")))
                .build();
        return new AwsStorageClient(recording.proxy(), presigner, "my-bucket", createContainerIfNotExists);
    }

    private static ContainerConfiguration configuration(boolean multipart) {
        return ContainerConfiguration.builder()
                .name("archive")
                .type("aws")
                .enableAutoMultiPartUpload(multipart)
                .multiPartUploadMinFileSize(FIVE_MIB)
                .multiPartUploadShardingSize(FIVE_MIB)
                .build();
    }

    private static StorageProviderSaveArgs args(
            ContainerConfiguration configuration, TrackingInputStream stream, long length, long threshold) {
        ContainerConfiguration actual = ContainerConfiguration.builder()
                .name(configuration.getName())
                .type(configuration.getType())
                .enableAutoMultiPartUpload(configuration.isEnableAutoMultiPartUpload())
                .multiPartUploadMinFileSize(threshold)
                .multiPartUploadShardingSize(Math.min(FIVE_MIB, threshold))
                .build();
        return new StorageProviderSaveArgs(
                "archive", actual, "a.txt", stream, length, ".txt", true, "text/plain", Map.of("source", "test"));
    }

    private static final class RecordingS3Client implements InvocationHandler {
        private final List<String> calls = new ArrayList<>();
        private final List<String> buckets = new ArrayList<>();
        private final List<String> uploadedBodies = new ArrayList<>();
        private final List<Integer> partNumbers = new ArrayList<>();
        private final List<Long> partLengths = new ArrayList<>();
        private final List<Integer> completedPartNumbers = new ArrayList<>();
        private final List<String> completedEtags = new ArrayList<>();
        private RuntimeException headBucketFailure;
        private int uploadPartFailureAt;
        private RuntimeException uploadPartFailure;
        private RuntimeException completeFailure;
        private RuntimeException abortFailure;

        private S3Client proxy() {
            return (S3Client)
                    Proxy.newProxyInstance(S3Client.class.getClassLoader(), new Class<?>[] {S3Client.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
            String name = method.getName();
            calls.add(name);
            if ("headBucket".equals(name)) {
                buckets.add(((HeadBucketRequest) args[0]).bucket());
                if (headBucketFailure != null) throw headBucketFailure;
            } else if ("createBucket".equals(name)) {
                buckets.add(((CreateBucketRequest) args[0]).bucket());
            } else if ("putObject".equals(name)) {
                uploadedBodies.add(read((RequestBody) args[1]));
            } else if ("createMultipartUpload".equals(name)) {
                return CreateMultipartUploadResponse.builder()
                        .uploadId("upload-1")
                        .build();
            } else if ("uploadPart".equals(name)) {
                UploadPartRequest request = (UploadPartRequest) args[0];
                partNumbers.add(request.partNumber());
                partLengths.add(request.contentLength());
                uploadedBodies.add(read((RequestBody) args[1]));
                if (request.partNumber() == uploadPartFailureAt) throw uploadPartFailure;
                return UploadPartResponse.builder()
                        .eTag("etag-" + request.partNumber())
                        .build();
            } else if ("completeMultipartUpload".equals(name)) {
                CompleteMultipartUploadRequest request = (CompleteMultipartUploadRequest) args[0];
                request.multipartUpload().parts().forEach(part -> {
                    completedPartNumbers.add(part.partNumber());
                    completedEtags.add(part.eTag());
                });
                if (completeFailure != null) throw completeFailure;
            } else if ("abortMultipartUpload".equals(name)) {
                AbortMultipartUploadRequest request = (AbortMultipartUploadRequest) args[0];
                assertThat(request.uploadId()).isEqualTo("upload-1");
                if (abortFailure != null) throw abortFailure;
            }
            return null;
        }

        private static String read(RequestBody body) throws IOException {
            try (var stream = body.contentStreamProvider().newStream()) {
                return new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
            }
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
