package io.github.cocosip.polystore.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cocosip.polystore.ContainerConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

class AwsCredentialsResolverTest {

    private Path profileDirectory;

    @BeforeEach
    void createProfileDirectory() throws IOException {
        profileDirectory = Files.createTempDirectory(Path.of("target"), "aws-profile-test-");
    }

    @AfterEach
    void deleteProfileDirectory() throws IOException {
        if (profileDirectory == null || !Files.exists(profileDirectory)) return;
        try (var paths = Files.walk(profileDirectory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static AwsStorageConfiguration configuration(Map<String, Object> properties) {
        return AwsStorageConfiguration.from(ContainerConfiguration.builder()
                .name("archive")
                .type("aws")
                .properties(properties)
                .build());
    }

    private static Map<String, Object> properties(Object... keyValues) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("region", "us-east-1");
        properties.put("containerName", "my-bucket");
        for (int i = 0; i < keyValues.length; i += 2) {
            properties.put((String) keyValues[i], keyValues[i + 1]);
        }
        return properties;
    }

    private static Map<String, Object> staticProperties() {
        return properties("accessKeyId", "AKIAIOSFODNN7EXAMPLE", "secretAccessKey", "wJalrXUtnFEMI");
    }

    @Test
    void staticModeShouldResolveTheConfiguredKeyPair() {
        AwsCredentials credentials = AwsCredentialsResolver.resolve(configuration(staticProperties()))
                .resolveCredentials();

        assertThat(credentials.accessKeyId()).isEqualTo("AKIAIOSFODNN7EXAMPLE");
        assertThat(credentials.secretAccessKey()).isEqualTo("wJalrXUtnFEMI");
    }

    @Test
    void profileModeShouldReadTheConfiguredCredentialsFile() throws IOException {
        Files.writeString(
                profileDirectory.resolve("credentials"),
                "[polystore]\naws_access_key_id = PROFILE_AK\naws_secret_access_key = PROFILE_SK\n");

        AwsCredentialsProvider provider = AwsCredentialsResolver.resolve(configuration(properties(
                "useCredentials", true, "profileName", "polystore", "profilesLocation", profileDirectory.toString())));

        AwsCredentials credentials = provider.resolveCredentials();
        assertThat(credentials.accessKeyId()).isEqualTo("PROFILE_AK");
        assertThat(credentials.secretAccessKey()).isEqualTo("PROFILE_SK");
    }

    @Test
    void profileModeShouldAcceptASingleCredentialsFile() throws IOException {
        Path file = profileDirectory.resolve("credentials");
        Files.writeString(file, "[polystore]\naws_access_key_id = FILE_AK\naws_secret_access_key = FILE_SK\n");

        AwsCredentialsProvider provider = AwsCredentialsResolver.resolve(configuration(
                properties("useCredentials", true, "profileName", "polystore", "profilesLocation", file.toString())));

        assertThat(provider.resolveCredentials().accessKeyId()).isEqualTo("FILE_AK");
    }

    @Test
    void useCredentialsWithoutProfileShouldFallBackToTheDefaultChain() {
        AwsCredentialsProvider provider =
                AwsCredentialsResolver.resolve(configuration(properties("useCredentials", true)));

        assertThat(provider).isNotNull();
        assertThat(provider.getClass().getSimpleName()).contains("DefaultCredentials");
    }

    @Test
    void temporaryCredentialsShouldShareOneProviderPerCacheKey() {
        AwsStorageConfiguration shared = configuration(
                properties("useTemporaryCredentials", true, "temporaryCredentialsCacheKey", "shared-cache-key"));
        AwsStorageConfiguration sameKey = configuration(
                properties("useTemporaryCredentials", true, "temporaryCredentialsCacheKey", "shared-cache-key"));
        AwsStorageConfiguration otherKey = configuration(
                properties("useTemporaryCredentials", true, "temporaryCredentialsCacheKey", "other-cache-key"));

        assertThat(AwsCredentialsResolver.resolve(shared)).isSameAs(AwsCredentialsResolver.resolve(sameKey));
        assertThat(AwsCredentialsResolver.resolve(shared)).isNotSameAs(AwsCredentialsResolver.resolve(otherKey));
    }

    @Test
    void staticModeWithoutKeysShouldBeRejected() {
        assertThatThrownBy(() -> AwsCredentialsResolver.resolve(configuration(properties())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accessKeyId");
    }

    @Test
    void federatedModeShouldRequireNameAndPolicy() {
        assertThatThrownBy(() -> AwsCredentialsResolver.resolve(configuration(properties(
                        "useTemporaryFederatedCredentials", true, "policy", "{\"Version\":\"2012-10-17\"}"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("name");
        assertThatThrownBy(() -> AwsCredentialsResolver.resolve(
                        configuration(properties("useTemporaryFederatedCredentials", true, "name", "session"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("policy");
    }

    @Test
    void configurationShouldApplyTheDefaultCacheKey() {
        AwsStorageConfiguration configuration = configuration(staticProperties());

        assertThat(configuration.temporaryCredentialsCacheKey()).isEqualTo("archive/aws");
        assertThat(configuration.createContainerIfNotExists()).isFalse();
    }
}
