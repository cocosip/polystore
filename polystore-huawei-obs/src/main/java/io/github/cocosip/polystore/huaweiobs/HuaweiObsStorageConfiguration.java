package io.github.cocosip.polystore.huaweiobs;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.util.ConfigUtils;

/**
 * Immutable parameters of one {@code huawei-obs} container, mirroring the reference
 * {@code ObsFileProviderConfiguration} of the C# <i>SharpAbp.Abp.FileStoring.Obs</i> framework.
 *
 * <p>{@link #from(ContainerConfiguration)} is the only place reading the raw container properties,
 * so the provider and the storage client never touch the property map.</p>
 *
 * @param endpoint                   OBS endpoint, e.g. {@code obs.cn-north-4.myhuaweicloud.com}
 * @param bucketName                 target bucket, never blank
 * @param accessKeyId                access key id of the access key pair
 * @param accessKeySecret            access key secret of the access key pair
 * @param createContainerIfNotExists create the bucket lazily, right before the first upload
 */
record HuaweiObsStorageConfiguration(
        String endpoint,
        String bucketName,
        String accessKeyId,
        String accessKeySecret,
        boolean createContainerIfNotExists) {

    /**
     * Parses and validates the {@code huawei-obs} parameters of the given container configuration.
     *
     * @param config container configuration carrying the provider parameters, never {@code null}
     * @return parsed configuration, never {@code null}
     * @throws IllegalStateException if {@code endpoint}, {@code accessKeyId}, {@code accessKeySecret}
     *                               or {@code bucketName} is missing or blank
     */
    static HuaweiObsStorageConfiguration from(ContainerConfiguration config) {
        var properties = config.getProperties();
        String endpoint = ConfigUtils.requireString(properties, "endpoint");
        String accessKeyId = ConfigUtils.requireString(properties, "accessKeyId");
        String accessKeySecret = ConfigUtils.requireString(properties, "accessKeySecret");
        String bucketName = ConfigUtils.requireString(properties, "bucketName");
        boolean createContainerIfNotExists = ConfigUtils.optBoolean(properties, "createContainerIfNotExists", false);

        return new HuaweiObsStorageConfiguration(
                endpoint, bucketName, accessKeyId, accessKeySecret, createContainerIfNotExists);
    }
}
