package io.github.cocosip.polystore.azure;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.StorageBackend;
import io.github.cocosip.polystore.StorageProvider;

/**
 * Storage provider of type {@code azure} (Azure Blob Storage). Parameter names follow the
 * reference <i>SharpAbp.Abp.FileStoring.Azure</i> {@code AzureFileProviderConfigurationNames}, so
 * they are configured in the container's {@code azure} section, e.g.
 * {@code azure: { connectionString: ..., containerName: ... }}.
 *
 * <p>Provider parameters:</p>
 * <ul>
 *   <li>{@code connectionString} — full service connection string; required unless the
 *       {@code accountName} + {@code accountKey} extension pair is supplied</li>
 *   <li>{@code containerName} (required) — target blob container</li>
 *   <li>{@code createContainerIfNotExists} (default {@code false}) — create the container lazily,
 *       right before the first upload, exactly like the reference provider; container construction
 *       never touches the network</li>
 *   <li>{@code accountName} + {@code accountKey} (Polystore extension) — credential pair used when
 *       no {@code connectionString} is given; the endpoint is derived as
 *       {@code https://<accountName>.blob.core.windows.net}</li>
 *   <li>{@code sasExpiry} (default {@code 3600}, Polystore extension) — SAS token validity in
 *       seconds for {@code getUrl}</li>
 * </ul>
 *
 * <p>SAS generation requires the account key, so a connection string without a key cannot sign
 * URLs.</p>
 */
public class AzureBlobStorageProvider implements StorageProvider {

    /** Provider type identifier of this backend. */
    public static final String TYPE = "azure";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public StorageBackend createBackend(ContainerConfiguration config) {
        AzureBlobStorageConfiguration configuration = AzureBlobStorageConfiguration.from(config);

        var builder = new BlobServiceClientBuilder();
        if (!configuration.connectionString().isEmpty()) {
            builder.connectionString(configuration.connectionString());
        } else {
            builder.endpoint("https://" + configuration.accountName() + ".blob.core.windows.net")
                    .credential(
                            new StorageSharedKeyCredential(configuration.accountName(), configuration.accountKey()));
        }

        BlobContainerClient containerClient =
                builder.buildClient().getBlobContainerClient(configuration.containerName());
        return new AzureBlobStorageClient(
                containerClient, configuration.sasExpirySeconds(), configuration.createContainerIfNotExists());
    }
}
