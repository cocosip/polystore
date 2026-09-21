package io.github.cocosip.polystore.ks3;

import com.ksyun.ks3.http.HttpClientConfig;
import com.ksyun.ks3.service.Ks3Client;
import com.ksyun.ks3.service.Ks3ClientConfig;

/**
 * Builds {@link Ks3Client} instances for the {@code ks3} provider, mapping the SharpAbp
 * {@code KS3FileProviderConfiguration} parameters onto the KS3 Java SDK.
 *
 * <p><b>Signature:</b> KS3 does not use AWS Signature Version 4. The KS3 Java SDK defaults to its
 * own {@code KSS} signature ({@code useAwsSignature = false} with signer version {@code V2}); this
 * factory leaves that default in place and only overrides it when the configuration explicitly asks
 * for another signer.</p>
 */
final class Ks3ConnectionFactory {

    private Ks3ConnectionFactory() {}

    /**
     * Creates the client described by the given configuration.
     *
     * @param configuration container parameters, never {@code null}
     * @return initialized KS3 client, never {@code null}
     */
    static Ks3Client create(Ks3StorageConfiguration configuration) {
        return new Ks3Client(configuration.accessKey(), configuration.secretKey(), clientConfig(configuration));
    }

    /**
     * Builds the SDK client configuration, including the KS3-native signer selection.
     *
     * @param configuration container parameters, never {@code null}
     * @return client configuration, never {@code null}
     */
    static Ks3ClientConfig clientConfig(Ks3StorageConfiguration configuration) {
        Ks3ClientConfig clientConfig = new Ks3ClientConfig();
        clientConfig.setEndpoint(configuration.endpoint());
        clientConfig.setProtocol(
                configuration.https() ? Ks3ClientConfig.PROTOCOL.https : Ks3ClientConfig.PROTOCOL.http);
        // KS3 native (KSS) signing is the SDK default and stays in place unless explicitly changed
        clientConfig.setUseAwsSignature(configuration.useAwsSignature());
        if (configuration.signerVersion() != null) {
            clientConfig.setVersion(configuration.signerVersion());
        }
        if (hasHttpClientSettings(configuration)) {
            HttpClientConfig httpClientConfig = clientConfig.getHttpClientConfig() == null
                    ? new HttpClientConfig()
                    : clientConfig.getHttpClientConfig();
            if (configuration.userAgent() != null) {
                httpClientConfig.setUserAgent(configuration.userAgent());
            }
            if (configuration.maxConnections() != null) {
                httpClientConfig.setMaxConnections(configuration.maxConnections());
            }
            if (configuration.timeout() != null) {
                httpClientConfig.setConnectionTimeout(configuration.timeout());
            }
            if (configuration.readWriteTimeout() != null) {
                httpClientConfig.setSocketTimeout(configuration.readWriteTimeout());
            }
            clientConfig.setHttpClientConfig(httpClientConfig);
        }
        return clientConfig;
    }

    private static boolean hasHttpClientSettings(Ks3StorageConfiguration configuration) {
        return configuration.userAgent() != null
                || configuration.maxConnections() != null
                || configuration.timeout() != null
                || configuration.readWriteTimeout() != null;
    }
}
