package io.github.cocosip.polystore.aliyunoss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aliyun.oss.OSS;
import io.github.cocosip.polystore.SaveArgs;
import io.github.cocosip.polystore.exception.StorageOperationException;
import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies the lazy bucket creation of {@link AliyunOssStorageClient} against a recording
 * {@link OSS} stub, so no network access is required.
 */
class AliyunOssStorageClientTest {

    private static final byte[] CONTENT = {1, 2, 3};

    private static AliyunOssStorageClient client(RecordingOss oss, boolean createContainerIfNotExists) {
        return new AliyunOssStorageClient(() -> oss.proxy(), "archive", 3600, createContainerIfNotExists);
    }

    private static void save(AliyunOssStorageClient client) {
        client.save("2024/report.pdf", new ByteArrayInputStream(CONTENT), SaveArgs.defaults());
    }

    @Test
    void saveShouldCreateBucketLazilyWhenMissing() {
        RecordingOss oss = new RecordingOss(false);

        save(client(oss, true));

        assertThat(oss.calls).containsExactly("doesBucketExist", "createBucket", "putObject");
    }

    @Test
    void saveShouldLeaveExistingBucketUntouched() {
        RecordingOss oss = new RecordingOss(true);

        save(client(oss, true));

        assertThat(oss.calls).containsExactly("doesBucketExist", "putObject");
    }

    @Test
    void saveShouldNotCheckTheBucketWhenCreationIsDisabled() {
        RecordingOss oss = new RecordingOss(false);

        save(client(oss, false));

        assertThat(oss.calls).containsExactly("putObject");
    }

    @Test
    void bucketCheckFailureShouldBeWrapped() {
        RecordingOss oss = new RecordingOss(false);
        oss.failOn = "doesBucketExist";

        assertThatThrownBy(() -> save(client(oss, true)))
                .isInstanceOf(StorageOperationException.class)
                .hasMessageContaining("Failed to check bucket");
    }

    @Test
    void bucketCreationFailureShouldBeWrapped() {
        RecordingOss oss = new RecordingOss(false);
        oss.failOn = "createBucket";

        assertThatThrownBy(() -> save(client(oss, true)))
                .isInstanceOf(StorageOperationException.class)
                .hasMessageContaining("Failed to create bucket");
    }

    /** Recording {@link OSS} stub; every call is recorded and returns a neutral value. */
    private static final class RecordingOss implements InvocationHandler {

        private final List<String> calls = new ArrayList<>();
        private final boolean bucketExists;
        private String failOn;

        private RecordingOss(boolean bucketExists) {
            this.bucketExists = bucketExists;
        }

        private OSS proxy() {
            return (OSS) Proxy.newProxyInstance(OSS.class.getClassLoader(), new Class<?>[] {OSS.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("toString".equals(name)) {
                return "RecordingOss";
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name)) {
                return proxy == args[0];
            }
            calls.add(name);
            if (name.equals(failOn)) {
                throw new IllegalStateException("stubbed failure: " + name);
            }
            if ("doesBucketExist".equals(name)) {
                return bucketExists;
            }
            return null;
        }
    }
}
