package io.github.cocosip.polystore.ks3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ksyun.ks3.dto.CompleteMultipartUploadResult;
import com.ksyun.ks3.dto.InitiateMultipartUploadResult;
import com.ksyun.ks3.dto.ObjectMetadata;
import com.ksyun.ks3.dto.PartETag;
import com.ksyun.ks3.dto.PutObjectResult;
import com.ksyun.ks3.exception.Ks3ServiceException;
import com.ksyun.ks3.service.Ks3Client;
import com.ksyun.ks3.service.request.InitiateMultipartUploadRequest;
import com.ksyun.ks3.service.request.UploadPartRequest;
import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.StorageProviderSaveArgs;
import io.github.cocosip.polystore.exception.StorageOperationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Ks3StorageClientTest {
    private static final long FIVE_MIB = 5L * 1024 * 1024;

    @Test
    void thresholdEqualityShouldUseSingleStreamingUploadWithoutClosingCallerStream() throws Exception {
        RecordingKs3Client sdk = new RecordingKs3Client();
        byte[] bytes = new byte[(int) FIVE_MIB + 1];
        bytes[(int) FIVE_MIB] = 9;
        TrackingInputStream stream = new TrackingInputStream(bytes);

        assertThat(client(sdk).save(args(stream, FIVE_MIB))).isEqualTo("a.bin");

        assertThat(sdk.calls).containsExactly("putObject");
        assertThat(sdk.singleLength).isEqualTo(FIVE_MIB);
        assertThat(stream.read()).isEqualTo(9);
        assertThat(stream.closed).isFalse();
    }

    @Test
    void multipartShouldUploadOrderedPartsAndShortFinalPart() {
        RecordingKs3Client sdk = new RecordingKs3Client();
        byte[] bytes = new byte[(int) FIVE_MIB + 3];

        client(sdk).save(args(new TrackingInputStream(bytes), bytes.length));

        assertThat(sdk.calls)
                .containsExactly("initiateMultipartUpload", "uploadPart", "uploadPart", "completeMultipartUpload");
        assertThat(sdk.partNumbers).containsExactly(1, 2);
        assertThat(sdk.partLengths).containsExactly(FIVE_MIB, 3L);
        assertThat(sdk.completedEtags).containsExactly("etag-1", "etag-2");
    }

    @Test
    void multipartFailureShouldAbortWithoutClosingCallerStream() {
        RecordingKs3Client sdk = new RecordingKs3Client();
        sdk.failPart = 2;
        TrackingInputStream stream = new TrackingInputStream(new byte[(int) FIVE_MIB + 1]);

        assertThatThrownBy(() -> client(sdk).save(args(stream, FIVE_MIB + 1)))
                .isInstanceOf(StorageOperationException.class)
                .hasRootCauseMessage("part failed");

        assertThat(sdk.calls).endsWith("uploadPart", "abortMultipartUpload");
        assertThat(stream.closed).isFalse();
    }

    private static Ks3StorageClient client(RecordingKs3Client sdk) {
        return new Ks3StorageClient(sdk, "archive", false);
    }

    private static StorageProviderSaveArgs args(TrackingInputStream stream, long length) {
        ContainerConfiguration configuration = ContainerConfiguration.builder()
                .name("archive")
                .type("ks3")
                .enableAutoMultiPartUpload(true)
                .multiPartUploadMinFileSize(FIVE_MIB)
                .multiPartUploadShardingSize(FIVE_MIB)
                .build();
        return new StorageProviderSaveArgs(
                "archive",
                configuration,
                "a.bin",
                stream,
                length,
                ".bin",
                true,
                "application/octet-stream",
                Map.of("source", "test"));
    }

    private static final class RecordingKs3Client extends Ks3Client {
        private final List<String> calls = new ArrayList<>();
        private final List<Integer> partNumbers = new ArrayList<>();
        private final List<Long> partLengths = new ArrayList<>();
        private final List<String> completedEtags = new ArrayList<>();
        private long singleLength;
        private int failPart;

        private RecordingKs3Client() {
            super("ak", "sk");
        }

        @Override
        public PutObjectResult putObject(String bucket, String key, InputStream input, ObjectMetadata metadata) {
            calls.add("putObject");
            singleLength = metadata.getContentLength();
            read(input);
            return new PutObjectResult();
        }

        @Override
        public InitiateMultipartUploadResult initiateMultipartUpload(InitiateMultipartUploadRequest request) {
            calls.add("initiateMultipartUpload");
            InitiateMultipartUploadResult result = new InitiateMultipartUploadResult();
            result.setUploadId("upload-1");
            return result;
        }

        @Override
        public PartETag uploadPart(UploadPartRequest request) {
            calls.add("uploadPart");
            partNumbers.add(request.getPartNumber());
            partLengths.add(request.getPartSize());
            read(request.getInputStream());
            if (request.getPartNumber() == failPart) {
                throw new IllegalStateException("part failed");
            }
            return new PartETag(request.getPartNumber(), "etag-" + request.getPartNumber());
        }

        @Override
        public CompleteMultipartUploadResult completeMultipartUpload(
                String bucket, String key, String uploadId, List<PartETag> parts) {
            calls.add("completeMultipartUpload");
            parts.stream().map(PartETag::geteTag).forEach(completedEtags::add);
            return new CompleteMultipartUploadResult();
        }

        @Override
        public com.ksyun.ks3.dto.Ks3Result abortMultipartUpload(String bucket, String key, String uploadId) {
            calls.add("abortMultipartUpload");
            return new com.ksyun.ks3.dto.Ks3Result();
        }

        @Override
        public boolean objectExists(String bucket, String key) throws Ks3ServiceException {
            return false;
        }

        private static void read(InputStream input) {
            try {
                input.readAllBytes();
            } catch (IOException e) {
                throw new IllegalStateException(e);
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
