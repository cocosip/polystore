package io.github.cocosip.polystore.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.StorageContainer;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AzureBlobStorageProviderTest {

    private static final String CONNECTION_STRING =
            "DefaultEndpointsProtocol=https;AccountName=devstore;AccountKey=a2V5;EndpointSuffix" + "=core.windows.net";

    private static StorageContainer build(Map<String, Object> properties) {
        return new AzureBlobStorageProvider()
                .createContainer(ContainerConfiguration.builder()
                        .name("blobs")
                        .type("azure")
                        .properties(properties)
                        .build());
    }

    @Test
    void providerTypeShouldBeAzure() {
        assertThat(new AzureBlobStorageProvider().getType()).isEqualTo("azure");
    }

    @Test
    void shouldBuildFromConnectionString() {
        StorageContainer container = build(Map.of("connectionString", CONNECTION_STRING, "containerName", "scans"));

        assertThat(container.getProviderType()).isEqualTo("azure");
    }

    @Test
    void shouldBuildFromAccountNameAndKey() {
        StorageContainer container = build(Map.of(
                "accountName", "devstore",
                "accountKey", "a2V5",
                "containerName", "scans"));

        assertThat(container.getProviderType()).isEqualTo("azure");
    }

    @Test
    void sasUrlShouldBeSignedOfflineAndHonorSasExpiry() {
        StorageContainer container = build(Map.of(
                "accountName", "devstore",
                "accountKey", "a2V5",
                "containerName", "scans",
                "sasExpiry", 120));

        String url = container.getUrl("dicom/1.dcm");

        assertThat(url).startsWith("https://devstore.blob.core.windows.net/scans/dicom%2F1.dcm?");
        assertThat(url).contains("sig=");
        assertThat(url).contains("se="); // signed expiry instant

        String explicit = container.getUrl(
                "dicom/1.dcm",
                io.github.cocosip.polystore.UrlArgs.builder()
                        .expiry(Duration.ofMinutes(10))
                        .build());
        assertThat(explicit).contains("sig=");
    }

    @Test
    void createContainerIfNotExistsShouldNotTouchTheNetworkAtConstruction() {
        // the container is created lazily on save, so an unreachable account must not fail
        // container construction even with the flag enabled
        StorageContainer container = build(Map.of(
                "accountName", "unreachableaccount",
                "accountKey", "a2V5",
                "containerName", "scans",
                "createContainerIfNotExists", true));

        assertThat(container.getProviderType()).isEqualTo("azure");
        assertThat(container.getName()).isEqualTo("blobs");
    }

    @Test
    void canonicalKeysShouldResolveRegardlessOfCaseAndSeparators() {
        StorageContainer container = build(Map.of(
                "Connection-String",
                CONNECTION_STRING,
                "Container_Name",
                "scans",
                "Create-Container-If-Not-Exists",
                true,
                "sas-expiry",
                60));

        assertThat(container.getProviderType()).isEqualTo("azure");
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
        assertThat(container.getUrl("dicom/1.dcm")).contains("sig=");
    }

    @Test
    void missingContainerNameShouldBeRejected() {
        assertThatThrownBy(() -> build(Map.of("connectionString", CONNECTION_STRING)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("containerName");
    }

    @Test
    void missingCredentialsShouldBeRejected() {
        assertThatThrownBy(() -> build(Map.of("containerName", "scans")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("connectionString");
    }
}
