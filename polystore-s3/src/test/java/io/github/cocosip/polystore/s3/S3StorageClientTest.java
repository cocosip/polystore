package io.github.cocosip.polystore.s3;

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
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3StorageClientTest {
    private static final long FIVE_MIB = 5L * 1024 * 1024;

    @Test
    void thresholdEqualityShouldStreamOnePutWithoutReadingPastTheLength() throws Exception {
        RecordingS3Client sdk = new RecordingS3Client();
        byte[] bytes = new byte[(int) FIVE_MIB + 1];
        bytes[(int) FIVE_MIB] = 9;
        TrackingInputStream stream = new TrackingInputStream(bytes);

        client(sdk, false).save(args(stream, FIVE_MIB));

        assertThat(sdk.calls).containsExactly("putObject");
        assertThat(sdk.lengths).containsExactly(FIVE_MIB);
        assertThat(stream.read()).isEqualTo(9);
        assertThat(stream.closed).isFalse();
    }

    @Test
    void multipartShouldCompleteOrderedEtagsAndAbortOnFailure() {
        RecordingS3Client sdk = new RecordingS3Client();
        byte[] bytes = new byte[(int) FIVE_MIB + 2];

        client(sdk, false).save(args(new TrackingInputStream(bytes), bytes.length));

        assertThat(sdk.calls)
                .containsExactly("createMultipartUpload", "uploadPart", "uploadPart", "completeMultipartUpload");
        assertThat(sdk.partNumbers).containsExactly(1, 2);
        assertThat(sdk.lengths).containsExactly(FIVE_MIB, 2L);
        assertThat(sdk.completedEtags).containsExactly("etag-1", "etag-2");

        RecordingS3Client failing = new RecordingS3Client();
        failing.failPart = 2;
        assertThatThrownBy(() -> client(failing, false).save(args(new TrackingInputStream(bytes), bytes.length)))
                .isInstanceOf(StorageOperationException.class)
                .hasRootCauseMessage("part failed");
        assertThat(failing.calls).endsWith("uploadPart", "abortMultipartUpload");
    }

    @Test
    void saveShouldCreateBucketOnNotFoundAndRejectEarlyEof() {
        RecordingS3Client sdk = new RecordingS3Client();
        sdk.headBucketFailure = NoSuchBucketException.builder().build();
        client(sdk, true).save(args(new TrackingInputStream(new byte[] {'x'}), 1));
        assertThat(sdk.calls).containsExactly("headBucket", "createBucket", "putObject");

        RecordingS3Client shortSdk = new RecordingS3Client();
        assertThatThrownBy(() -> client(shortSdk, false)
                        .save(args(new TrackingInputStream(new byte[(int) FIVE_MIB]), FIVE_MIB + 1)))
                .isInstanceOf(StorageOperationException.class)
                .hasRootCauseInstanceOf(java.io.EOFException.class);
        assertThat(shortSdk.calls).endsWith("abortMultipartUpload");
    }

    @Test
    void non404BucketFailureShouldBeReported() {
        RecordingS3Client sdk = new RecordingS3Client();
        sdk.headBucketFailure = S3Exception.builder().statusCode(403).build();
        assertThatThrownBy(() -> client(sdk, true).save(args(new TrackingInputStream(new byte[] {'x'}), 1)))
                .isInstanceOf(StorageOperationException.class)
                .hasMessageContaining("Failed to check bucket");
    }

    private static S3StorageClient client(RecordingS3Client sdk, boolean create) {
        S3Presigner presigner = S3Presigner.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("ak", "sk")))
                .endpointOverride(URI.create("http://ceph.internal:7480"))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
        return new S3StorageClient(sdk.proxy(), presigner, "backup", 60, create);
    }

    private static StorageProviderSaveArgs args(TrackingInputStream stream, long length) {
        ContainerConfiguration config = ContainerConfiguration.builder()
                .name("backup")
                .type("s3")
                .enableAutoMultiPartUpload(true)
                .multiPartUploadMinFileSize(FIVE_MIB)
                .multiPartUploadShardingSize(FIVE_MIB)
                .build();
        return new StorageProviderSaveArgs(
                "backup", config, "a.txt", stream, length, ".txt", true, "text/plain", Map.of("source", "test"));
    }

    private static final class RecordingS3Client implements InvocationHandler {
        private final List<String> calls = new ArrayList<>();
        private final List<Integer> partNumbers = new ArrayList<>();
        private final List<Long> lengths = new ArrayList<>();
        private final List<String> completedEtags = new ArrayList<>();
        private RuntimeException headBucketFailure;
        private int failPart;

        private S3Client proxy() {
            return (S3Client)
                    Proxy.newProxyInstance(S3Client.class.getClassLoader(), new Class<?>[] {S3Client.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
            String name = method.getName();
            calls.add(name);
            if ("headBucket".equals(name) && headBucketFailure != null) throw headBucketFailure;
            if ("putObject".equals(name)) {
                RequestBody body = (RequestBody) args[1];
                lengths.add(body.contentLength());
                consume(body);
            } else if ("createMultipartUpload".equals(name)) {
                return CreateMultipartUploadResponse.builder().uploadId("u1").build();
            } else if ("uploadPart".equals(name)) {
                UploadPartRequest request = (UploadPartRequest) args[0];
                partNumbers.add(request.partNumber());
                lengths.add(request.contentLength());
                consume((RequestBody) args[1]);
                if (request.partNumber() == failPart) throw new IllegalStateException("part failed");
                return UploadPartResponse.builder()
                        .eTag("etag-" + request.partNumber())
                        .build();
            } else if ("completeMultipartUpload".equals(name)) {
                CompleteMultipartUploadRequest request = (CompleteMultipartUploadRequest) args[0];
                request.multipartUpload().parts().forEach(part -> completedEtags.add(part.eTag()));
            }
            return null;
        }

        private void consume(RequestBody body) throws IOException {
            try (var stream = body.contentStreamProvider().newStream()) {
                stream.readAllBytes();
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
