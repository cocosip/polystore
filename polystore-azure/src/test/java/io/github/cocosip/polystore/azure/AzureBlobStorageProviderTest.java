package io.github.cocosip.polystore.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.DefaultStorageContainer;
import io.github.cocosip.polystore.StorageContainer;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AzureBlobStorageProviderTest {
    private static final String CONNECTION_STRING =
            "DefaultEndpointsProtocol=https;AccountName=devstore;AccountKey=a2V5;EndpointSuffix=core.windows.net";

    private static ContainerConfiguration configuration(Map<String, Object> properties) {
        return ContainerConfiguration.builder()
                .name("blobs")
                .type("azure")
                .properties(properties)
                .build();
    }

    private static StorageContainer build(Map<String, Object> properties) {
        ContainerConfiguration configuration = configuration(properties);
        AzureBlobStorageProvider provider = new AzureBlobStorageProvider();
        return DefaultStorageContainer.from(configuration, provider.createBackend(configuration));
    }

    @Test
    void providerTypeShouldBeAzure() {
        assertThat(new AzureBlobStorageProvider().getType()).isEqualTo("azure");
    }

    @Test
    void shouldBuildFromConnectionStringAndAccountCredentials() {
        assertThat(build(Map.of("connectionString", CONNECTION_STRING, "containerName", "scans"))
                        .getProviderType())
                .isEqualTo("azure");
        assertThat(build(Map.of(
                                "accountName", "devstore",
                                "accountKey", "a2V5",
                                "containerName", "scans"))
                        .getProviderType())
                .isEqualTo("azure");
    }

    @Test
    void sasUrlShouldBeSignedOfflineForTheExplicitExpiry() {
        StorageContainer container =
                build(Map.of("accountName", "devstore", "accountKey", "a2V5", "containerName", "scans"));

        String url = container.getAccessUrl("dicom/1.dcm", Instant.parse("2026-09-22T00:00:00Z"), false);

        assertThat(url).startsWith("https://devstore.blob.core.windows.net/scans/dicom%2F1.dcm?");
        assertThat(url).contains("sig=").contains("se=");
    }

    @Test
    void unknownSasExpiryKeyShouldBeIgnoredWithoutBreakingParsing() {
        AzureBlobStorageConfiguration configuration = AzureBlobStorageConfiguration.from(configuration(Map.of(
                "accountName", "devstore",
                "accountKey", "a2V5",
                "containerName", "scans",
                "sasExpiry", 120)));

        assertThat(configuration.containerName()).isEqualTo("scans");
    }

    @Test
    void constructionShouldRemainOfflineAndNormalizedKeysShouldResolve() {
        StorageContainer container = build(Map.of(
                "Account-Name", "unreachableaccount",
                "Account_Key", "a2V5",
                "Container-Name", "scans",
                "Create-Container-If-Not-Exists", true));
        assertThat(container.getName()).isEqualTo("blobs");
    }

    @Test
    void sharpAbpQualifiedKeysShouldResolve() {
        StorageContainer container = build(Map.of(
                "Azure.ConnectionString",
                CONNECTION_STRING,
                "Azure.ContainerName",
                "scans",
                "Azure.CreateContainerIfNotExists",
                true));
        assertThat(container.getProviderType()).isEqualTo("azure");
    }

    @Test
    void missingRequiredValuesShouldBeRejected() {
        assertThatThrownBy(() -> build(Map.of("connectionString", CONNECTION_STRING)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("containerName");
        assertThatThrownBy(() -> build(Map.of("containerName", "scans")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("connectionString");
    }
}
