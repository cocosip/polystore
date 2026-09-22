package io.github.cocosip.polystore.huaweiobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.obs.services.ObsClient;
import com.obs.services.model.AbortMultipartUploadRequest;
import com.obs.services.model.CompleteMultipartUploadRequest;
import com.obs.services.model.HeaderResponse;
import com.obs.services.model.InitiateMultipartUploadRequest;
import com.obs.services.model.InitiateMultipartUploadResult;
import com.obs.services.model.PartEtag;
import com.obs.services.model.PutObjectRequest;
import com.obs.services.model.PutObjectResult;
import com.obs.services.model.UploadPartRequest;
import com.obs.services.model.UploadPartResult;
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

class HuaweiObsStorageClientTest {
    private static final long FIVE_MIB = 5L * 1024 * 1024;

    @Test
    void thresholdEqualityShouldUseSingleStreamingUploadWithoutClosingCallerStream() throws Exception {
        RecordingObsClient sdk = new RecordingObsClient();
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
        RecordingObsClient sdk = new RecordingObsClient();
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
        RecordingObsClient sdk = new RecordingObsClient();
        sdk.failPart = 2;
        TrackingInputStream stream = new TrackingInputStream(new byte[(int) FIVE_MIB + 1]);

        assertThatThrownBy(() -> client(sdk).save(args(stream, FIVE_MIB + 1)))
                .isInstanceOf(StorageOperationException.class)
                .hasRootCauseMessage("part failed");

        assertThat(sdk.calls).endsWith("uploadPart", "abortMultipartUpload");
        assertThat(stream.closed).isFalse();
    }

    private static HuaweiObsStorageClient client(RecordingObsClient sdk) {
        return new HuaweiObsStorageClient(sdk, "dicom", false);
    }

    private static StorageProviderSaveArgs args(TrackingInputStream stream, long length) {
        ContainerConfiguration configuration = ContainerConfiguration.builder()
                .name("dicom")
                .type("huawei-obs")
                .enableAutoMultiPartUpload(true)
                .multiPartUploadMinFileSize(FIVE_MIB)
                .multiPartUploadShardingSize(FIVE_MIB)
                .build();
        return new StorageProviderSaveArgs(
                "dicom",
                configuration,
                "a.bin",
                stream,
                length,
                ".bin",
                true,
                "application/octet-stream",
                Map.of("source", "test"));
    }

    private static final class RecordingObsClient extends ObsClient {
        private final List<String> calls = new ArrayList<>();
        private final List<Integer> partNumbers = new ArrayList<>();
        private final List<Long> partLengths = new ArrayList<>();
        private final List<String> completedEtags = new ArrayList<>();
        private long singleLength;
        private int failPart;

        private RecordingObsClient() {
            super("ak", "sk", "obs.cn-north-4.myhuaweicloud.com");
        }

        @Override
        public PutObjectResult putObject(PutObjectRequest request) {
            calls.add("putObject");
            singleLength = request.getMetadata().getContentLength();
            read(request.getInput());
            return null;
        }

        @Override
        public InitiateMultipartUploadResult initiateMultipartUpload(InitiateMultipartUploadRequest request) {
            calls.add("initiateMultipartUpload");
            return new InitiateMultipartUploadResult("dicom", "a.bin", "upload-1");
        }

        @Override
        public UploadPartResult uploadPart(UploadPartRequest request) {
            calls.add("uploadPart");
            partNumbers.add(request.getPartNumber());
            partLengths.add(request.getPartSize());
            read(request.getInput());
            if (request.getPartNumber() == failPart) {
                throw new IllegalStateException("part failed");
            }
            UploadPartResult result = new UploadPartResult();
            result.setPartNumber(request.getPartNumber());
            result.setEtag("etag-" + request.getPartNumber());
            return result;
        }

        @Override
        public com.obs.services.model.CompleteMultipartUploadResult completeMultipartUpload(
                CompleteMultipartUploadRequest request) {
            calls.add("completeMultipartUpload");
            request.getPartEtag().stream().map(PartEtag::getEtag).forEach(completedEtags::add);
            return null;
        }

        @Override
        public HeaderResponse abortMultipartUpload(AbortMultipartUploadRequest request) {
            calls.add("abortMultipartUpload");
            return new HeaderResponse();
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
