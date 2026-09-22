package io.github.cocosip.polystore.azure;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.util.ConfigUtils;

/**
 * Immutable parameters of one {@code azure} container, mirroring the reference
 * {@code AzureFileProviderConfiguration} of the C# <i>SharpAbp.Abp.FileStoring.Azure</i> framework.
 *
 * <p>{@link #from(ContainerConfiguration)} is the only place reading the raw container properties,
 * so the provider and the storage client never touch the property map.</p>
 *
 * @param connectionString           full service connection string, empty when the account name and
 *                                   key pair is used instead
 * @param accountName                storage account name used when no connection string is given
 * @param accountKey                 storage account key used when no connection string is given
 * @param containerName              target blob container, never blank
 * @param createContainerIfNotExists create the blob container lazily, right before the first upload
 */
record AzureBlobStorageConfiguration(
        String connectionString,
        String accountName,
        String accountKey,
        String containerName,
        boolean createContainerIfNotExists) {

    /**
     * Parses and validates the {@code azure} parameters of the given container configuration.
     *
     * @param config container configuration carrying the provider parameters, never {@code null}
     * @return parsed configuration, never {@code null}
     * @throws IllegalStateException if {@code containerName} is missing or blank, or if neither
     *                               {@code connectionString} nor the {@code accountName} +
     *                               {@code accountKey} pair is supplied
     */
    static AzureBlobStorageConfiguration from(ContainerConfiguration config) {
        var properties = config.getProperties();
        String connectionString = ConfigUtils.optString(properties, "connectionString", "");
        String accountName = ConfigUtils.optString(properties, "accountName", "");
        String accountKey = ConfigUtils.optString(properties, "accountKey", "");
        String containerName = ConfigUtils.requireString(properties, "containerName");
        boolean createContainerIfNotExists = ConfigUtils.optBoolean(properties, "createContainerIfNotExists", false);

        if (connectionString.isEmpty() && (accountName.isEmpty() || accountKey.isEmpty())) {
            throw new IllegalStateException("Azure credentials require either 'connectionString' or the 'accountName'"
                    + " + 'accountKey' pair");
        }
        return new AzureBlobStorageConfiguration(
                connectionString, accountName, accountKey, containerName, createContainerIfNotExists);
    }
}
