package io.github.cocosip.polystore.ks3;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.StorageBackend;
import io.github.cocosip.polystore.StorageProvider;
import java.util.Collection;
import java.util.List;

/**
 * Storage provider of type {@code ks3}: Kingsoft Cloud KS3, backed by the KS3 Java SDK.
 *
 * <p>KS3 is <b>not</b> an S3-compatible store: it authenticates requests with its own {@code KSS}
 * signature, so it uses its dedicated SDK and signer instead of the {@code s3} / {@code aws}
 * providers. Provider parameters, named after {@code KS3FileProviderConfigurationNames}:</p>
 * <ul>
 *   <li>{@code endpoint} (required) — service host, e.g. {@code ks3-cn-beijing.ksyuncs.com}; a
 *       scheme in the value is accepted and used when {@code protocol} is absent</li>
 *   <li>{@code bucketName} (required) — bucket name</li>
 *   <li>{@code accessKey} / {@code secretKey} (required) — credentials</li>
 *   <li>{@code protocol} (default {@code http}) — {@code http} or {@code https}</li>
 *   <li>{@code userAgent} (default SDK default)</li>
 *   <li>{@code maxConnections} (default SDK default) — connection pool size</li>
 *   <li>{@code timeout} (default SDK default) — connection timeout in milliseconds</li>
 *   <li>{@code readWriteTimeout} (default SDK default) — socket read/write timeout in
 *       milliseconds</li>
 *   <li>{@code createContainerIfNotExists} (default {@code false}) — create the bucket lazily before
 *       the first upload</li>
 *   <li>{@code signerVersion} (default SDK default {@code V2}, Polystore extension) — KS3 signer
 *       version {@code V2}, {@code V4} or {@code V4_UNSIGNED_PAYLOAD_SIGNER}</li>
 *   <li>{@code useAwsSignature} (default {@code false}, Polystore extension) — use the AWS signature
 *       instead of the KS3 native one; leave it {@code false} for KS3 itself</li>
 * </ul>
 */
public class Ks3StorageProvider implements StorageProvider {

    /** Provider type identifier of this backend. */
    public static final String TYPE = "ks3";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public Collection<String> getAliases() {
        return List.of("KS3");
    }

    @Override
    public StorageBackend createBackend(ContainerConfiguration config) {
        Ks3StorageConfiguration configuration = Ks3StorageConfiguration.from(config);
        return new Ks3StorageClient(
                Ks3ConnectionFactory.create(configuration),
                configuration.bucketName(),
                configuration.createContainerIfNotExists());
    }
}
