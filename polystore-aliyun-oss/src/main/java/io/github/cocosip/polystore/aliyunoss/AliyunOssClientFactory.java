package io.github.cocosip.polystore.aliyunoss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import java.util.function.Supplier;

/**
 * Builds OSS clients for the {@code aliyun-oss} provider, mirroring the reference
 * {@code DefaultOssClientFactory}: a sub-account key pair is used as is, while
 * {@code useSecurityTokenService} signs OSS requests with STS temporary credentials obtained via
 * {@code AssumeRole}.
 *
 * <p>In STS mode the client is assembled with {@link AliyunOssStsCredentialsProvider}, which
 * resolves credentials from {@link AliyunTemporaryCredentialsCache} per request and re-assumes the
 * role once they expire — a long-running client therefore survives session rotations. Client
 * creation is lazy, so building a container never performs a network call — not even in STS mode,
 * where the first {@code AssumeRole} request is only sent on the first OSS operation.</p>
 */
final class AliyunOssClientFactory {

    private AliyunOssClientFactory() {}

    /**
     * Returns a memoizing supplier of the OSS client described by the given configuration.
     *
     * @param configuration container parameters, never {@code null}
     * @return supplier creating the client on first use and reusing it afterwards
     */
    static Supplier<OSS> lazyClient(AliyunOssStorageConfiguration configuration) {
        return new MemoizingOssClientSupplier(configuration);
    }

    /**
     * Builds the OSS client described by the given configuration. In STS mode the credentials stay
     * refreshable through {@link AliyunOssStsCredentialsProvider} instead of being baked into the
     * client.
     *
     * @param configuration container parameters, never {@code null}
     * @return initialized OSS client, never {@code null}
     */
    static OSS create(AliyunOssStorageConfiguration configuration) {
        if (!configuration.useSecurityTokenService()) {
            return new OSSClientBuilder()
                    .build(configuration.endpoint(), configuration.accessKeyId(), configuration.accessKeySecret());
        }
        return new OSSClientBuilder()
                .build(configuration.endpoint(), new AliyunOssStsCredentialsProvider(configuration));
    }

    /** Creates the OSS client on first use, so container construction stays network-free. */
    private static final class MemoizingOssClientSupplier implements Supplier<OSS> {

        private final AliyunOssStorageConfiguration configuration;
        private OSS client;

        private MemoizingOssClientSupplier(AliyunOssStorageConfiguration configuration) {
            this.configuration = configuration;
        }

        @Override
        public synchronized OSS get() {
            if (client == null) {
                client = create(configuration);
            }
            return client;
        }
    }
}
