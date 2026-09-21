package io.github.cocosip.polystore.ks3;

import static org.assertj.core.api.Assertions.assertThat;

import com.ksyun.ks3.http.HttpClientConfig;
import com.ksyun.ks3.service.Ks3Client;
import com.ksyun.ks3.service.Ks3ClientConfig;
import org.junit.jupiter.api.Test;

class Ks3ConnectionFactoryTest {

    private static Ks3StorageConfiguration configuration(boolean https) {
        return new Ks3StorageConfiguration(
                "archive",
                "ks3-cn-beijing.ksyuncs.com",
                "ak",
                "sk",
                https,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                3600);
    }

    @Test
    void ks3NativeSignatureShouldBeTheDefault() {
        // KS3 does not use the AWS signature: useAwsSignature must stay false and the signer
        // version must remain the SDK default (V2)
        Ks3ClientConfig clientConfig = Ks3ConnectionFactory.clientConfig(configuration(false));

        assertThat(clientConfig.isUseAwsSignature()).isFalse();
        assertThat(clientConfig.getVersion()).isEqualTo(Ks3ClientConfig.SignerVersion.V2);
    }

    @Test
    void theCreatedClientShouldUseTheKs3NativeSignature() {
        Ks3Client client = Ks3ConnectionFactory.create(configuration(true));

        assertThat(client.getClientConfig().isUseAwsSignature()).isFalse();
        assertThat(client.getClientConfig().getProtocol()).isEqualTo(Ks3ClientConfig.PROTOCOL.https);
    }

    @Test
    void endpointAndProtocolShouldBeApplied() {
        Ks3ClientConfig http = Ks3ConnectionFactory.clientConfig(configuration(false));
        assertThat(http.getEndpoint()).isEqualTo("ks3-cn-beijing.ksyuncs.com");
        assertThat(http.getProtocol()).isEqualTo(Ks3ClientConfig.PROTOCOL.http);

        Ks3ClientConfig https = Ks3ConnectionFactory.clientConfig(configuration(true));
        assertThat(https.getProtocol()).isEqualTo(Ks3ClientConfig.PROTOCOL.https);
    }

    @Test
    void signerVersionShouldBeAppliedWhenConfigured() {
        Ks3StorageConfiguration v4 = new Ks3StorageConfiguration(
                "archive",
                "ks3-cn-beijing.ksyuncs.com",
                "ak",
                "sk",
                false,
                null,
                null,
                null,
                null,
                Ks3ClientConfig.SignerVersion.V4,
                false,
                false,
                3600);

        assertThat(Ks3ConnectionFactory.clientConfig(v4).getVersion()).isEqualTo(Ks3ClientConfig.SignerVersion.V4);
    }

    @Test
    void awsSignatureShouldOnlyBeEnabledExplicitly() {
        Ks3StorageConfiguration aws = new Ks3StorageConfiguration(
                "archive",
                "ks3-cn-beijing.ksyuncs.com",
                "ak",
                "sk",
                false,
                null,
                null,
                null,
                null,
                null,
                true,
                false,
                3600);

        assertThat(Ks3ConnectionFactory.clientConfig(aws).isUseAwsSignature()).isTrue();
    }

    @Test
    void httpClientSettingsShouldBeAppliedWhenConfigured() {
        Ks3StorageConfiguration configured = new Ks3StorageConfiguration(
                "archive",
                "ks3-cn-beijing.ksyuncs.com",
                "ak",
                "sk",
                false,
                "polystore-test",
                32,
                5000,
                6000,
                null,
                false,
                false,
                3600);

        HttpClientConfig httpClientConfig =
                Ks3ConnectionFactory.clientConfig(configured).getHttpClientConfig();

        assertThat(httpClientConfig).isNotNull();
        assertThat(httpClientConfig.getUserAgent()).isEqualTo("polystore-test");
        assertThat(httpClientConfig.getMaxConnections()).isEqualTo(32);
        assertThat(httpClientConfig.getConnectionTimeout()).isEqualTo(5000);
        assertThat(httpClientConfig.getSocketTimeout()).isEqualTo(6000);
    }
}
