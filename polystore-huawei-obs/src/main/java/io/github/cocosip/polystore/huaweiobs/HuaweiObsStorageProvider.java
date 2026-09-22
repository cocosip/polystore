package io.github.cocosip.polystore.huaweiobs;

import com.obs.services.ObsClient;
import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.StorageBackend;
import io.github.cocosip.polystore.StorageProvider;
import java.util.Collection;
import java.util.List;

/**
 * Storage provider of type {@code huawei-obs} (Huawei Cloud OBS). Parameter names follow the
 * reference <i>SharpAbp.Abp.FileStoring.Obs</i> {@code ObsFileProviderConfigurationNames}, so they
 * are configured in the container's {@code huawei-obs} section, e.g.
 * {@code huawei-obs: { endpoint: ..., bucketName: ... }}.
 *
 * <p>Provider parameters:</p>
 * <ul>
 *   <li>{@code endpoint} (required) — OBS endpoint, e.g.
 *       {@code obs.cn-north-4.myhuaweicloud.com}</li>
 *   <li>{@code bucketName} (required) — target bucket</li>
 *   <li>{@code accessKeyId} / {@code accessKeySecret} (required) — access key pair</li>
 *   <li>{@code createContainerIfNotExists} (default {@code false}) — create the bucket lazily,
 *       right before the first upload, exactly like the reference provider; container construction
 *       never touches the network</li>
 * </ul>
 */
public class HuaweiObsStorageProvider implements StorageProvider {

    /** Creates the provider. */
    public HuaweiObsStorageProvider() {}

    /** Provider type identifier of this backend. */
    public static final String TYPE = "huawei-obs";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public Collection<String> getAliases() {
        return List.of("Obs");
    }

    @Override
    public StorageBackend createBackend(ContainerConfiguration config) {
        HuaweiObsStorageConfiguration configuration = HuaweiObsStorageConfiguration.from(config);

        ObsClient client =
                new ObsClient(configuration.accessKeyId(), configuration.accessKeySecret(), configuration.endpoint());
        return new HuaweiObsStorageClient(
                client, configuration.bucketName(), configuration.createContainerIfNotExists());
    }
}
