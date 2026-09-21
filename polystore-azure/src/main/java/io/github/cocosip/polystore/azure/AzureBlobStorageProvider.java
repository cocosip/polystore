package io.github.cocosip.polystore.azure;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.DefaultStorageContainer;
import io.github.cocosip.polystore.StorageContainer;
import io.github.cocosip.polystore.StorageProvider;
import io.github.cocosip.polystore.util.ConfigUtils;

/**
 * Storage provider of type {@code azure} (Azure Blob Storage).
 *
 * <p>Parameters: {@code containerName} (required); credentials are either a
 * {@code connectionString} or an {@code accountName} + {@code accountKey} pair; plus
 * {@code sasExpiry} (default 3600 seconds, SAS token validity for {@code getUrl}). SAS
 * generation requires the account key, so a connection string without a key cannot sign URLs.</p>
 */
public class AzureBlobStorageProvider implements StorageProvider {

    /** Provider type identifier of this backend. */
    public static final String TYPE = "azure";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public StorageContainer createContainer(ContainerConfiguration config) {
        var properties = config.getProperties();
        String connectionString = ConfigUtils.optString(properties, "connectionString", "");
        String accountName = ConfigUtils.optString(properties, "accountName", "");
        String accountKey = ConfigUtils.optString(properties, "accountKey", "");
        String containerName = ConfigUtils.requireString(properties, "containerName");
        long sasExpiry = ConfigUtils.optLong(properties, "sasExpiry", 3600);

        var builder = new BlobServiceClientBuilder();
        if (!connectionString.isEmpty()) {
            builder.connectionString(connectionString);
        } else if (!accountName.isEmpty() && !accountKey.isEmpty()) {
            builder.endpoint("https://" + accountName + ".blob.core.windows.net")
                    .credential(new StorageSharedKeyCredential(accountName, accountKey));
        } else {
            throw new IllegalStateException("Azure credentials require either 'connectionString' or the 'accountName'"
                    + " + 'accountKey' pair");
        }

        BlobContainerClient containerClient = builder.buildClient().getBlobContainerClient(containerName);
        return DefaultStorageContainer.from(config, new AzureBlobStorageClient(containerClient, sasExpiry));
    }
}
