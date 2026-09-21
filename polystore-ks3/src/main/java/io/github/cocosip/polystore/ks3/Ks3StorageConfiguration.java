package io.github.cocosip.polystore.ks3;

import com.ksyun.ks3.service.Ks3ClientConfig;
import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.util.ConfigUtils;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable parameters of one {@code ks3} container, mirroring the reference
 * {@code KS3FileProviderConfiguration} of the C# <i>SharpAbp.Abp.FileStoring.KS3</i> framework.
 *
 * <p>Unlike Amazon S3 and the S3-compatible stores, KS3 authenticates with its own {@code KSS}
 * signature rather than AWS Signature Version 4; {@code useAwsSignature} therefore stays
 * {@code false} unless it is explicitly enabled for an AWS-compatible gateway.</p>
 *
 * @param bucketName                  target bucket
 * @param endpoint                    service host without scheme, e.g.
 *                                    {@code ks3-cn-beijing.ksyuncs.com}
 * @param accessKey                   access key
 * @param secretKey                   secret key
 * @param https                       use HTTPS
 * @param userAgent                   HTTP user agent, {@code null} for the SDK default
 * @param maxConnections              connection pool size, {@code null} for the SDK default
 * @param timeout                     connection timeout in milliseconds, {@code null} for the SDK
 *                                    default
 * @param readWriteTimeout            socket read/write timeout in milliseconds, {@code null} for
 *                                    the SDK default
 * @param signerVersion               KS3 signer version, {@code null} for the SDK default ({@code V2})
 * @param useAwsSignature             use the AWS signature instead of the KS3 native one; must stay
 *                                    {@code false} for KS3 itself
 * @param createContainerIfNotExists  create the bucket before the first upload when absent
 * @param urlExpirySeconds            presigned URL expiry in seconds
 */
record Ks3StorageConfiguration(
        String bucketName,
        String endpoint,
        String accessKey,
        String secretKey,
        boolean https,
        String userAgent,
        Integer maxConnections,
        Integer timeout,
        Integer readWriteTimeout,
        Ks3ClientConfig.SignerVersion signerVersion,
        boolean useAwsSignature,
        boolean createContainerIfNotExists,
        int urlExpirySeconds) {

    /**
     * Parses the provider parameters, applying the reference defaults and validating the required
     * and malformed values.
     *
     * @param config container configuration, never {@code null}
     * @return parsed configuration, never {@code null}
     * @throws IllegalStateException if a required parameter is missing or a value is invalid
     */
    static Ks3StorageConfiguration from(ContainerConfiguration config) {
        Map<String, Object> properties = config.getProperties();
        String endpoint = ConfigUtils.requireString(properties, "endpoint");
        String bucketName = ConfigUtils.requireString(properties, "bucketName");
        String accessKey = ConfigUtils.requireString(properties, "accessKey");
        String secretKey = ConfigUtils.requireString(properties, "secretKey");
        boolean https = resolveHttps(properties, endpoint);
        String userAgent = ConfigUtils.optString(properties, "userAgent", null);
        Integer maxConnections = optPositiveInt(properties, "maxConnections");
        Integer timeout = optPositiveInt(properties, "timeout");
        Integer readWriteTimeout = optPositiveInt(properties, "readWriteTimeout");
        Ks3ClientConfig.SignerVersion signerVersion = resolveSignerVersion(properties);
        boolean useAwsSignature = ConfigUtils.optBoolean(properties, "useAwsSignature", false);
        boolean createContainerIfNotExists = ConfigUtils.optBoolean(properties, "createContainerIfNotExists", false);
        int urlExpiry = ConfigUtils.optInt(properties, "urlExpiry", 3600);

        return new Ks3StorageConfiguration(
                bucketName,
                stripScheme(endpoint, config.getName()),
                accessKey,
                secretKey,
                https,
                userAgent,
                maxConnections,
                timeout,
                readWriteTimeout,
                signerVersion,
                useAwsSignature,
                createContainerIfNotExists,
                urlExpiry);
    }

    private static boolean resolveHttps(Map<String, Object> properties, String endpoint) {
        String protocol = ConfigUtils.optString(properties, "protocol", "");
        if (protocol.isEmpty()) {
            return endpoint.startsWith("https://");
        }
        if ("https".equalsIgnoreCase(protocol)) {
            return true;
        }
        if ("http".equalsIgnoreCase(protocol)) {
            return false;
        }
        throw new IllegalStateException(
                "Invalid storage parameter 'protocol': " + protocol + " (expected 'http' or 'https')");
    }

    /**
     * Removes the scheme from the endpoint, because the SDK takes the host and the protocol
     * separately.
     */
    private static String stripScheme(String endpoint, String containerName) {
        String host = endpoint;
        if (host.startsWith("https://")) {
            host = host.substring("https://".length());
        } else if (host.startsWith("http://")) {
            host = host.substring("http://".length());
        }
        while (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.isEmpty()) {
            throw new IllegalStateException(
                    "Invalid storage parameter 'endpoint' of container '" + containerName + "': " + endpoint);
        }
        return host;
    }

    private static Ks3ClientConfig.SignerVersion resolveSignerVersion(Map<String, Object> properties) {
        String value = ConfigUtils.optString(properties, "signerVersion", "");
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Ks3ClientConfig.SignerVersion.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid storage parameter 'signerVersion': " + value
                    + " (expected 'V2', 'V4' or 'V4_UNSIGNED_PAYLOAD_SIGNER')");
        }
    }

    private static Integer optPositiveInt(Map<String, Object> properties, String key) {
        Object raw = ConfigUtils.get(properties, key);
        if (raw == null || String.valueOf(raw).trim().isEmpty()) {
            return null;
        }
        int value = ConfigUtils.optInt(properties, key, -1);
        if (value <= 0) {
            throw new IllegalStateException("Invalid storage parameter '" + key + "': " + raw);
        }
        return value;
    }
}
