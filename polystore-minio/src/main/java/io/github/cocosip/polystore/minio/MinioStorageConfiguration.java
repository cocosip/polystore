package io.github.cocosip.polystore.minio;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.util.ConfigUtils;

/**
 * Immutable parameters of one {@code minio} container, mirroring
 * {@code MinioFileProviderConfigurationNames} of the reference <i>SharpAbp.Abp.FileStoring</i>
 * framework.
 *
 * @param endPoint                MinIO service endpoint; a scheme prefix is added when the
 *                                configured value has none, taken from {@code withSSL}
 * @param accessKey               access key, required
 * @param secretKey               secret key, required
 * @param bucketName              bucket name, required
 * @param region                  signing region, default {@code us-east-1} (Polystore extension)
 * @param urlExpirySeconds        presigned URL expiry in seconds, default {@code 3600} (Polystore
 *                                extension)
 * @param createBucketIfNotExists create the bucket lazily before the first upload, default
 *                                {@code false}
 */
record MinioStorageConfiguration(
        String endPoint,
        String accessKey,
        String secretKey,
        String bucketName,
        String region,
        int urlExpirySeconds,
        boolean createBucketIfNotExists) {

    /**
     * Parses and validates the parameters of one {@code minio} container.
     *
     * @param config container configuration holding the provider parameters
     * @return parsed configuration, never {@code null}
     * @throws IllegalStateException if a required parameter is missing
     */
    static MinioStorageConfiguration from(ContainerConfiguration config) {
        var properties = config.getProperties();
        String endPoint = ConfigUtils.requireString(properties, "endPoint");
        String accessKey = ConfigUtils.requireString(properties, "accessKey");
        String secretKey = ConfigUtils.requireString(properties, "secretKey");
        String bucketName = ConfigUtils.requireString(properties, "bucketName");
        boolean withSSL = ConfigUtils.optBoolean(properties, "withSSL", false);
        boolean createBucketIfNotExists = ConfigUtils.optBoolean(properties, "createBucketIfNotExists", false);
        String region = ConfigUtils.optString(properties, "region", "us-east-1");
        int urlExpirySeconds = ConfigUtils.optInt(properties, "urlExpiry", 3600);
        return new MinioStorageConfiguration(
                resolveEndpoint(endPoint, withSSL),
                accessKey,
                secretKey,
                bucketName,
                region,
                urlExpirySeconds,
                createBucketIfNotExists);
    }

    /**
     * Adds the scheme implied by {@code withSSL} when the configured endpoint has none.
     *
     * @param endPoint configured endpoint
     * @param withSSL  whether HTTPS should be used
     * @return endpoint with a scheme, never {@code null}
     */
    private static String resolveEndpoint(String endPoint, boolean withSSL) {
        if (endPoint.startsWith("http://") || endPoint.startsWith("https://")) {
            return endPoint;
        }
        return (withSSL ? "https://" : "http://") + endPoint;
    }
}
