package io.github.cocosip.polystore.huaweiobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.obs.services.ObsClient;
import com.obs.services.exception.ObsException;
import com.obs.services.model.ObjectMetadata;
import com.obs.services.model.ObsBucket;
import com.obs.services.model.PutObjectResult;
import io.github.cocosip.polystore.SaveArgs;
import io.github.cocosip.polystore.exception.StorageOperationException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies the lazy bucket creation of {@link HuaweiObsStorageClient} against a recording
 * {@link ObsClient} stub, so no network access is required.
 */
class HuaweiObsStorageClientTest {

    private static final byte[] CONTENT = {1, 2, 3};

    private static HuaweiObsStorageClient client(RecordingObsClient obs, boolean createContainerIfNotExists) {
        return new HuaweiObsStorageClient(obs, "dicom", 3600, createContainerIfNotExists);
    }

    private static void save(HuaweiObsStorageClient client) {
        client.save("2024/scan.dcm", new ByteArrayInputStream(CONTENT), SaveArgs.defaults());
    }

    private static ObsException obsException(String errorCode, int responseCode) {
        ObsException exception = new ObsException("stubbed OBS failure: " + errorCode);
        exception.setErrorCode(errorCode);
        exception.setResponseCode(responseCode);
        return exception;
    }

    @Test
    void saveShouldCreateBucketLazilyWhenMissing() {
        RecordingObsClient obs = new RecordingObsClient(false);

        save(client(obs, true));

        assertThat(obs.calls).containsExactly("headBucket", "createBucket", "putObject");
    }

    @Test
    void saveShouldLeaveExistingBucketUntouched() {
        RecordingObsClient obs = new RecordingObsClient(true);

        save(client(obs, true));

        assertThat(obs.calls).containsExactly("headBucket", "putObject");
    }

    @Test
    void missingBucketExceptionShouldCountAsAbsent() {
        RecordingObsClient obs = new RecordingObsClient(false);
        obs.headBucketFailure = obsException("NoSuchBucket", 404);

        save(client(obs, true));

        assertThat(obs.calls).containsExactly("headBucket", "createBucket", "putObject");
    }

    @Test
    void unexpectedBucketCheckFailureShouldBeWrapped() {
        RecordingObsClient obs = new RecordingObsClient(false);
        obs.headBucketFailure = obsException("AccessDenied", 403);

        assertThatThrownBy(() -> save(client(obs, true)))
                .isInstanceOf(StorageOperationException.class)
                .hasMessageContaining("Failed to check bucket");
        assertThat(obs.calls).containsExactly("headBucket");
    }

    @Test
    void bucketCreationFailureShouldBeWrapped() {
        RecordingObsClient obs = new RecordingObsClient(false);
        obs.failOn = "createBucket";

        assertThatThrownBy(() -> save(client(obs, true)))
                .isInstanceOf(StorageOperationException.class)
                .hasMessageContaining("Failed to create bucket");
    }

    @Test
    void saveShouldNotCheckTheBucketWhenCreationIsDisabled() {
        RecordingObsClient obs = new RecordingObsClient(false);

        save(client(obs, false));

        assertThat(obs.calls).containsExactly("putObject");
    }

    /** Recording {@link ObsClient} stub; every call is recorded and returns a neutral value. */
    private static final class RecordingObsClient extends ObsClient {

        private final List<String> calls = new ArrayList<>();
        private final boolean bucketExists;
        private ObsException headBucketFailure;
        private String failOn;

        private RecordingObsClient(boolean bucketExists) {
            super("ak", "sk", "obs.cn-north-4.myhuaweicloud.com");
            this.bucketExists = bucketExists;
        }

        @Override
        public boolean headBucket(String bucketName) {
            calls.add("headBucket");
            if (headBucketFailure != null) {
                throw headBucketFailure;
            }
            return bucketExists;
        }

        @Override
        public ObsBucket createBucket(String bucketName) {
            calls.add("createBucket");
            failIfRequested("createBucket");
            return null;
        }

        @Override
        public PutObjectResult putObject(
                String bucketName, String objectKey, InputStream input, ObjectMetadata metadata) {
            calls.add("putObject");
            failIfRequested("putObject");
            return null;
        }

        private void failIfRequested(String call) {
            if (call.equals(failOn)) {
                throw new ObsException("stubbed failure: " + call);
            }
        }
    }
}
