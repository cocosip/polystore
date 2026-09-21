package io.github.cocosip.polystore.huaweiobs;

import com.obs.services.ObsClient;
import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.DefaultStorageContainer;
import io.github.cocosip.polystore.StorageContainer;
import io.github.cocosip.polystore.StorageProvider;
import io.github.cocosip.polystore.util.ConfigUtils;

/**
 * Storage provider of type {@code huawei-obs}.
 *
 * <p>Parameters: {@code endpoint} (required), {@code accessKey} / {@code secretKey} /
 * {@code bucketName} (required) and {@code urlExpiry} (default 3600 seconds, signed URL
 * expiry).</p>
 */
public class HuaweiObsStorageProvider implements StorageProvider {

    /** Provider type identifier of this backend. */
    public static final String TYPE = "huawei-obs";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public StorageContainer createContainer(ContainerConfiguration config) {
        var properties = config.getProperties();
        String endpoint = ConfigUtils.requireString(properties, "endpoint");
        String accessKey = ConfigUtils.requireString(properties, "accessKey");
        String secretKey = ConfigUtils.requireString(properties, "secretKey");
        String bucketName = ConfigUtils.requireString(properties, "bucketName");
        long urlExpiry = ConfigUtils.optLong(properties, "urlExpiry", 3600);

        ObsClient client = new ObsClient(accessKey, secretKey, endpoint);
        return DefaultStorageContainer.from(config, new HuaweiObsStorageClient(client, bucketName, urlExpiry));
    }
}
