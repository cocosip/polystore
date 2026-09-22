package io.github.cocosip.polystore.aliyunoss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.CompleteMultipartUploadRequest;
import com.aliyun.oss.model.InitiateMultipartUploadResult;
import com.aliyun.oss.model.PartETag;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.UploadPartRequest;
import com.aliyun.oss.model.UploadPartResult;
import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.StorageProviderSaveArgs;
import io.github.cocosip.polystore.exception.StorageOperationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AliyunOssStorageClientTest {
    private static final long FIVE_MIB = 5L * 1024 * 1024;

    @Test
    void singleAndMultipartUploadsShouldHonorLengthAndOrder() throws Exception {
        RecordingOss oss = new RecordingOss();
        byte[] single = new byte[(int) FIVE_MIB + 1];
        single[(int) FIVE_MIB] = 9;
        TrackingInputStream stream = new TrackingInputStream(single);
        client(oss).save(args(stream, FIVE_MIB));
        assertThat(oss.calls).containsExactly("putObject");
        assertThat(oss.lengths).containsExactly(FIVE_MIB);
        assertThat(stream.read()).isEqualTo(9);
        oss.reset();
        client(oss).save(args(new TrackingInputStream(single), FIVE_MIB + 1));
        assertThat(oss.calls)
                .containsExactly("initiateMultipartUpload", "uploadPart", "uploadPart", "completeMultipartUpload");
        assertThat(oss.partNumbers).containsExactly(1, 2);
        assertThat(oss.completed).containsExactly("etag-1", "etag-2");
    }

    @Test
    void multipartFailureShouldAbortAndPreserveCallerStream() {
        RecordingOss oss = new RecordingOss();
        oss.failPart = 2;
        TrackingInputStream stream = new TrackingInputStream(new byte[(int) FIVE_MIB + 1]);
        assertThatThrownBy(() -> client(oss).save(args(stream, FIVE_MIB + 1)))
                .isInstanceOf(StorageOperationException.class)
                .hasRootCauseMessage("part failed");
        assertThat(oss.calls).endsWith("uploadPart", "abortMultipartUpload");
        assertThat(stream.closed).isFalse();
    }

    private static AliyunOssStorageClient client(RecordingOss oss) {
        return new AliyunOssStorageClient(() -> oss.proxy(), "archive", false);
    }

    private static StorageProviderSaveArgs args(TrackingInputStream stream, long length) {
        ContainerConfiguration config = ContainerConfiguration.builder()
                .name("archive")
                .type("aliyun-oss")
                .enableAutoMultiPartUpload(true)
                .multiPartUploadMinFileSize(FIVE_MIB)
                .multiPartUploadShardingSize(FIVE_MIB)
                .build();
        return new StorageProviderSaveArgs(
                "archive",
                config,
                "a.bin",
                stream,
                length,
                ".bin",
                true,
                "application/octet-stream",
                Map.of("source", "test"));
    }

    private static final class RecordingOss implements InvocationHandler {
        final List<String> calls = new ArrayList<>();
        final List<Integer> partNumbers = new ArrayList<>();
        final List<Long> lengths = new ArrayList<>();
        final List<String> completed = new ArrayList<>();
        int failPart;

        OSS proxy() {
            return (OSS) Proxy.newProxyInstance(OSS.class.getClassLoader(), new Class<?>[] {OSS.class}, this);
        }

        void reset() {
            calls.clear();
            partNumbers.clear();
            lengths.clear();
            completed.clear();
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
            String n = method.getName();
            if (n.equals("toString")) return "RecordingOss";
            if (n.equals("hashCode")) return 1;
            if (n.equals("equals")) return proxy == args[0];
            calls.add(n);
            if (n.equals("putObject")) {
                PutObjectRequest r = (PutObjectRequest) args[0];
                lengths.add(r.getMetadata().getContentLength());
                r.getInputStream().readAllBytes();
            }
            if (n.equals("initiateMultipartUpload")) {
                var r = new InitiateMultipartUploadResult();
                r.setUploadId("u1");
                return r;
            }
            if (n.equals("uploadPart")) {
                UploadPartRequest r = (UploadPartRequest) args[0];
                partNumbers.add(r.getPartNumber());
                lengths.add(r.getPartSize());
                r.getInputStream().readAllBytes();
                if (r.getPartNumber() == failPart) throw new IllegalStateException("part failed");
                UploadPartResult result = new UploadPartResult();
                result.setPartNumber(r.getPartNumber());
                result.setETag("etag-" + r.getPartNumber());
                return result;
            }
            if (n.equals("completeMultipartUpload")) {
                CompleteMultipartUploadRequest r = (CompleteMultipartUploadRequest) args[0];
                r.getPartETags().stream().map(PartETag::getETag).forEach(completed::add);
            }
            return null;
        }
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        boolean closed;

        TrackingInputStream(byte[] b) {
            super(b);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
