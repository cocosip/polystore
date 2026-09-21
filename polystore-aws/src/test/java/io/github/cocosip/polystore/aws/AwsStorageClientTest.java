package io.github.cocosip.polystore.aws;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.cocosip.polystore.SaveArgs;
import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class AwsStorageClientTest {

    private static AwsStorageClient client(RecordingS3Client recording, boolean createContainerIfNotExists) {
        S3Presigner presigner = S3Presigner.builder()
                .region(Region.of("us-east-1"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("ak", "sk")))
                .build();
        return new AwsStorageClient(recording.proxy(), presigner, "my-bucket", 3600, createContainerIfNotExists);
    }

    private static void save(AwsStorageClient client) {
        client.save("a.txt", new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)), SaveArgs.defaults());
    }

    @Test
    void saveShouldCreateTheBucketWhenHeadBucketRaisesNoSuchBucket() {
        RecordingS3Client recording = new RecordingS3Client();
        recording.headBucketFailure = NoSuchBucketException.builder().build();

        save(client(recording, true));

        assertThat(recording.calls).containsExactly("headBucket", "createBucket", "putObject");
        assertThat(recording.buckets).containsExactly("my-bucket", "my-bucket");
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

    /** Records the SDK calls, so the lazy bucket creation sequence can be asserted. */
    private static final class RecordingS3Client implements InvocationHandler {

        private final List<String> calls = new ArrayList<>();
        private final List<String> buckets = new ArrayList<>();
        private RuntimeException headBucketFailure;

        private S3Client proxy() {
            return (S3Client)
                    Proxy.newProxyInstance(S3Client.class.getClassLoader(), new Class<?>[] {S3Client.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            calls.add(name);
            if (args != null && args.length > 0) {
                if (args[0] instanceof HeadBucketRequest request) {
                    buckets.add(request.bucket());
                } else if (args[0] instanceof CreateBucketRequest request) {
                    buckets.add(request.bucket());
                }
            }
            if ("headBucket".equals(name) && headBucketFailure != null) {
                throw headBucketFailure;
            }
            return null;
        }
    }
}
