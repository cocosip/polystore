package io.github.cocosip.polystore.s3;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.util.ConfigUtils;
import java.net.URI;
import java.util.Map;

/**
 * Immutable parameters of one {@code s3} container, named after
 * {@code S3FileProviderConfigurationNames} of the reference <i>SharpAbp.Abp.FileStoring</i>
 * framework.
 *
 * @param endpoint                service endpoint URI, built from {@code serverUrl} and
 *                                {@code protocol}
 * @param accessKeyId             access key id, required
 * @param secretAccessKey         secret access key, required
 * @param bucketName              bucket name, required
 * @param forcePathStyle          path-style addressing, default {@code false}
 * @param useChunkEncoding        AWS chunked payload signing, default {@code false}
 * @param authenticationRegion    region used for AWS Signature Version 4, default
 *                                {@code us-east-1}
 * @param createBucketIfNotExists create the bucket lazily before the first upload, default
 *                                {@code false}
 */
record S3StorageConfiguration(
        URI endpoint,
        String accessKeyId,
        String secretAccessKey,
        String bucketName,
        boolean forcePathStyle,
        boolean useChunkEncoding,
        String authenticationRegion,
        boolean createBucketIfNotExists) {

    /** Protocol value of {@code S3.Protocol} meaning HTTP. */
    private static final int PROTOCOL_HTTP = 1;

    /** Protocol value of {@code S3.Protocol} meaning HTTPS. */
    private static final int PROTOCOL_HTTPS = 2;

    /** Region used for signature version 4 when {@code authenticationRegion} is absent. */
    private static final String DEFAULT_AUTHENTICATION_REGION = "us-east-1";

    /**
     * Parses and validates the parameters of one {@code s3} container.
     *
     * @param config container configuration holding the provider parameters
     * @return parsed configuration, never {@code null}
     * @throws IllegalStateException if a required parameter is missing or {@code serverUrl} is not
     *                               a valid URI or carries no host
     */
    static S3StorageConfiguration from(ContainerConfiguration config) {
        var properties = config.getProperties();
        String serverUrl = ConfigUtils.requireString(properties, "serverUrl");
        String accessKeyId = ConfigUtils.requireString(properties, "accessKeyId");
        String secretAccessKey = ConfigUtils.requireString(properties, "secretAccessKey");
        String bucketName = ConfigUtils.requireString(properties, "bucketName");
        boolean forcePathStyle = ConfigUtils.optBoolean(properties, "forcePathStyle", false);
        boolean useChunkEncoding = ConfigUtils.optBoolean(properties, "useChunkEncoding", false);
        String authenticationRegion =
                ConfigUtils.optString(properties, "authenticationRegion", DEFAULT_AUTHENTICATION_REGION);
        boolean createBucketIfNotExists = ConfigUtils.optBoolean(properties, "createBucketIfNotExists", false);
        return new S3StorageConfiguration(
                resolveEndpoint(serverUrl, properties),
                accessKeyId,
                secretAccessKey,
                bucketName,
                forcePathStyle,
                useChunkEncoding,
                authenticationRegion,
                createBucketIfNotExists);
    }

    /**
     * Builds the endpoint URI, prefixing the scheme implied by {@code protocol} when the configured
     * server URL has none.
     *
     * @param serverUrl  configured server URL
     * @param properties provider parameters
     * @return endpoint URI, never {@code null}
     * @throws IllegalStateException if the server URL is not a valid URI or carries no host
     */
    private static URI resolveEndpoint(String serverUrl, Map<String, Object> properties) {
        String candidate = serverUrl;
        if (!candidate.startsWith("http://") && !candidate.startsWith("https://")) {
            candidate = resolveScheme(properties) + "://" + candidate;
        }
        try {
            URI uri = URI.create(candidate);
            if (uri.getHost() == null || uri.getHost().isEmpty()) {
                throw new IllegalStateException("Invalid storage parameter 'serverUrl', host is missing: " + serverUrl);
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid storage parameter 'serverUrl': " + serverUrl, e);
        }
    }

    /**
     * Resolves the URL scheme from {@code protocol}, accepting both the numeric reference form
     * ({@code 1}/{@code 2}) and the textual one ({@code HTTP}/{@code HTTPS}).
     *
     * @param properties provider parameters
     * @return {@code http} or {@code https}, never {@code null}
     */
    private static String resolveScheme(Map<String, Object> properties) {
        Object raw = ConfigUtils.get(properties, "protocol");
        String value = raw == null ? "" : String.valueOf(raw).trim();
        if (value.equalsIgnoreCase("https") || String.valueOf(PROTOCOL_HTTPS).equals(value)) {
            return "https";
        }
        if (value.equalsIgnoreCase("http") || String.valueOf(PROTOCOL_HTTP).equals(value)) {
            return "http";
        }
        return ConfigUtils.optInt(properties, "protocol", PROTOCOL_HTTP) == PROTOCOL_HTTPS ? "https" : "http";
    }
}
