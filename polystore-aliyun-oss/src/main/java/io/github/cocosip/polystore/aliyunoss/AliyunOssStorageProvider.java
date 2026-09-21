package io.github.cocosip.polystore.aliyunoss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.DefaultStorageContainer;
import io.github.cocosip.polystore.StorageContainer;
import io.github.cocosip.polystore.StorageProvider;
import io.github.cocosip.polystore.util.ConfigUtils;

/**
 * Storage provider of type {@code aliyun-oss}.
 *
 * <p>Parameters: {@code endpoint} (required, e.g. {@code oss-cn-hangzhou.aliyuncs.com}),
 * {@code accessKeyId} / {@code accessKeySecret} / {@code bucketName} (required),
 * {@code urlExpiry} (default 3600 seconds, presigned URL expiry) and {@code useInternal}
 * (default {@code false}; rewrites {@code .aliyuncs.com} endpoints to the {@code -internal}
 * intranet variant).</p>
 */
public class AliyunOssStorageProvider implements StorageProvider {

    /** Provider type identifier of this backend. */
    public static final String TYPE = "aliyun-oss";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public StorageContainer createContainer(ContainerConfiguration config) {
        var properties = config.getProperties();
        String endpoint = ConfigUtils.requireString(properties, "endpoint");
        String accessKeyId = ConfigUtils.requireString(properties, "accessKeyId");
        String accessKeySecret = ConfigUtils.requireString(properties, "accessKeySecret");
        String bucketName = ConfigUtils.requireString(properties, "bucketName");
        long urlExpiry = ConfigUtils.optLong(properties, "urlExpiry", 3600);
        boolean useInternal = ConfigUtils.optBoolean(properties, "useInternal", false);

        String resolvedEndpoint = useInternal ? endpoint.replace(".aliyuncs.com", "-internal.aliyuncs.com") : endpoint;
        OSS client = new OSSClientBuilder().build(resolvedEndpoint, accessKeyId, accessKeySecret);
        return DefaultStorageContainer.from(config, new AliyunOssStorageClient(client, bucketName, urlExpiry));
    }
}
