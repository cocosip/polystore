package io.github.cocosip.polystore.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cocosip.polystore.SaveArgs;
import io.github.cocosip.polystore.exception.StorageOperationException;
import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3StorageClientTest {

    private static S3Presigner presigner() {
        return S3Presigner.builder()
                .region(Region.of("us-east-1"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("ak", "sk")))
                .endpointOverride(URI.create("http://ceph.internal:7480"))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private static S3StorageClient client(RecordingS3Client recording, boolean createBucketIfNotExists) {
        return new S3StorageClient(recording.proxy(), presigner(), "backup", 60, createBucketIfNotExists);
    }

    private static void save(S3StorageClient client) {
        client.save("a.txt", new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)), SaveArgs.defaults());
    }

    @Test
    void saveShouldCreateTheBucketWhenHeadBucketReports404() {
        RecordingS3Client recording = new RecordingS3Client();
        recording.headBucketFailure = S3Exception.builder().statusCode(404).build();

        save(client(recording, true));

        assertThat(recording.calls).containsExactly("headBucket", "createBucket", "putObject");
    }

    @Test
    void saveShouldCreateTheBucketWhenHeadBucketRaisesNoSuchBucket() {
        RecordingS3Client recording = new RecordingS3Client();
        recording.headBucketFailure = NoSuchBucketException.builder().build();

        save(client(recording, true));

        assertThat(recording.calls).containsExactly("headBucket", "createBucket", "putObject");
    }

    @Test
    void saveShouldNotCreateAnExistingBucket() {
        RecordingS3Client recording = new RecordingS3Client();

        save(client(recording, true));

        assertThat(recording.calls).containsExactly("headBucket", "putObject");
    }

    @Test
    void saveShouldSkipTheCheckWhenCreationIsDisabled() {
        RecordingS3Client recording = new RecordingS3Client();

        save(client(recording, false));

        assertThat(recording.calls).containsExactly("putObject");
    }

    @Test
    void saveShouldFailWhenTheBucketCheckFailsForAnotherReason() {
        RecordingS3Client recording = new RecordingS3Client();
        recording.headBucketFailure = S3Exception.builder().statusCode(403).build();

        assertThatThrownBy(() -> save(client(recording, true)))
                .isInstanceOf(StorageOperationException.class)
                .hasMessageContaining("Failed to check bucket");
        assertThat(recording.calls).containsExactly("headBucket");
    }

    @Test
    void saveShouldFailWhenTheBucketCannotBeCreated() {
        RecordingS3Client recording = new RecordingS3Client();
        recording.headBucketFailure = S3Exception.builder().statusCode(404).build();
        recording.createBucketFailure = S3Exception.builder().statusCode(403).build();

        assertThatThrownBy(() -> save(client(recording, true)))
                .isInstanceOf(StorageOperationException.class)
                .hasMessageContaining("Failed to create bucket");
        assertThat(recording.calls).containsExactly("headBucket", "createBucket");
    }

    /** Records the SDK calls and fails the configured ones, so the save sequence can be asserted. */
    private static final class RecordingS3Client implements InvocationHandler {

        private final List<String> calls = new ArrayList<>();
        private RuntimeException headBucketFailure;
        private RuntimeException createBucketFailure;

        private S3Client proxy() {
            return (S3Client)
                    Proxy.newProxyInstance(S3Client.class.getClassLoader(), new Class<?>[] {S3Client.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            calls.add(name);
            if ("headBucket".equals(name) && headBucketFailure != null) {
                throw headBucketFailure;
            }
            if ("createBucket".equals(name) && createBucketFailure != null) {
                throw createBucketFailure;
            }
            return null;
        }
    }
}
