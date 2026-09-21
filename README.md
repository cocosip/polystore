# Polystore

Polystore is a pluggable file storage abstraction for Java 21. Business code programs against one
unified `StorageClient` API, a single process hosts multiple named containers, and each container
independently selects its storage backend through an SPI. Spring Boot integration is provided by a
separate starter module.

The project is currently versioned as `0.1.0-SNAPSHOT`.

Further design material lives in the [`docs/`](docs/) index: the
[architecture & functional design](docs/architecture.md) and the
[development plan](docs/development-plan.md).

## Features

- **Unified API** — save / get / delete / exists / getUrl / deleteAll, independent of the backend.
- **Multiple containers** — several containers per process, each with its own backend and
  credentials; one of them can be marked as the default.
- **Pluggable backends** — SPI registration via Spring beans or `ServiceLoader`; adding a backend
  module to the classpath is enough.
- **Tenant path isolation** — a per-container `PATH_PREFIX` mode transparently prefixes every file
  path with the resolved tenant id.
- **Storage events** — `FileSavedEvent` / `FileDeletedEvent` published as Spring application
  events.
- **Spring Boot starter** — yml-driven configuration with IDE autocomplete support.
- **Framework-free core** — `polystore-core` depends only on the SLF4J API and works without
  Spring via a plain builder.

## Requirements

- OpenJDK 21
- Maven 3.9 or newer

The repository includes Maven Wrapper scripts. Use `mvnw.cmd` on Windows and `./mvnw` on Linux or
macOS.

## Modules

| Module | Purpose |
|---|---|
| `polystore-core` | Core interfaces and types; no Spring dependency |
| `polystore-spring-boot-starter` | yml configuration, manager assembly, events, auto-configuration |
| `polystore-local` | Local filesystem backend |
| `polystore-minio` | MinIO backend (MinIO Java SDK) |
| `polystore-s3` | S3-**compatible** stores — Ceph, KS3, MinIO, R2, ... (AWS SDK v2) |
| `polystore-aws` | Amazon Web Services S3 only (AWS SDK v2) |
| `polystore-ks3` | Kingsoft Cloud KS3 (KS3 Java SDK, native `KSS` signature) |
| `polystore-azure` | Azure Blob Storage backend |
| `polystore-aliyun-oss` | Alibaba Cloud OSS backend |
| `polystore-huawei-obs` | Huawei Cloud OBS backend |
| `polystore-sftp` | SFTP backend (JSch with a connection pool) |

## Dependency

```xml
<dependency>
  <groupId>io.github.cocosip</groupId>
  <artifactId>polystore-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
<!-- plus one backend module per container type, e.g. -->
<dependency>
  <groupId>io.github.cocosip</groupId>
  <artifactId>polystore-local</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Quick start

Configure containers in `application.yml` and inject the `StorageManager`:

```yaml
polystore:
  containers:
    - name: images
      type: local
      default: true
      local:
        base-path: /data/files
        append-container-name-to-base-path: true
        http-server: https://cdn.example.com

    - name: dicom
      type: minio
      tenant-isolation: PATH_PREFIX
      minio:
        end-point: minio.internal:9000
        access-key: admin
        secret-key: password
        bucket-name: dicom
        with-ssl: true
        create-bucket-if-not-exists: true
```

```java
@Service
public class DicomService {

    private final StorageManager storageManager;

    public DicomService(StorageManager storageManager) {
        this.storageManager = storageManager;
    }

    public void store(InputStream content) {
        storageManager.getContainer("dicom")
                .save("2024/01/scan.dcm", content, SaveArgs.builder()
                        .contentType("application/dicom")
                        .tenantId("tenant-abc")   // explicit override; a registered
                        .build());                // TenantIdSupplier bean is used otherwise
    }

    public InputStream load(String fileName) {
        return storageManager.getContainer("dicom").get(fileName);
    }
}
```

Provider-specific parameters (the `minio:` block above) live in a sibling key named after the
container's `type`; the section key and the `type` value are matched case- and
separator-insensitively, so `type: Minio` reads a `minio:` block and `type: Aliyun-Oss` reads an
`aliyun_oss:` block. Parameter names follow the reference
[SharpAbp.Abp.FileStoring](https://github.com/cocosip/sharp-abp) configuration names, and are
matched case- and separator-insensitively too — `end-point:`, `endPoint:` and the qualified
`Minio.EndPoint:` form all resolve to the same parameter. The reference provider names that differ
from the Polystore type ids are accepted as aliases: `FileSystem` (local), `Aliyun` (aliyun-oss),
`Obs` (huawei-obs) and `KS3` (ks3).

### Tenant isolation

Containers with `tenant-isolation: PATH_PREFIX` prefix every path with the resolved tenant id:
`images/photo.jpg` is stored as `{tenantId}/images/photo.jpg`. The id resolves as
`SaveArgs.tenantId` (explicit) → registered `TenantIdSupplier` bean (implicit) → error
(`TenantIdMissingException`). Register the supplier once:

```java
@Bean
TenantIdSupplier tenantIdSupplier() {
    return () -> MyTenantContext.currentTenantId();
}
```

### Storage events

Save and delete operations publish Spring application events:

```java
@EventListener
void onSaved(FileSavedEvent event) {
    log.info("saved {} into {} ({})", event.getFileName(), event.getContainerName(),
            event.getProviderType());
}
```

### Without Spring

```java
StorageManager manager = PolystoreBuilder.builder()
        .addProvider(new LocalStorageProvider())
        .addConfiguration(ContainerConfiguration.builder()
                .name("images")
                .type("local")
                .isDefault(true)
                .property("basePath", "/data/images")
                .build())
        .tenantIdSupplier(() -> MyTenantContext.currentTenantId())
        .eventPublisher(events::add)
        .build();
```

## Backend configuration

Parameter names mirror the reference `SharpAbp.Abp.FileStoring.{Provider}` configuration names;
parameters marked *extension* exist only in Polystore. `create*IfNotExists` flags create the
bucket/container **lazily on the first save**, so container startup never touches the network.

### local (reference provider: `FileSystem`)

| Parameter | Required | Default | Description |
|---|---|---|---|
| `basePath` | yes | — | Storage root directory |
| `appendContainerNameToBasePath` | no | `true` | Store under `{basePath}/{containerName}` |
| `httpServer` | no | `""` | HTTP prefix returned by `getUrl` |
| `createDirectories` | no | `true` | Create missing directories on save (*extension*) |

### minio

| Parameter | Required | Default | Description |
|---|---|---|---|
| `endPoint` | yes | — | MinIO service address (scheme optional) |
| `accessKey` / `secretKey` | yes | — | Credentials |
| `bucketName` | yes | — | Bucket name |
| `withSSL` | no | `false` | Use HTTPS |
| `createBucketIfNotExists` | no | `false` | Create the bucket on first save |
| `region` | no | `us-east-1` | Signing region; keeps `getUrl` local instead of a bucket-location lookup (*extension*) |
| `urlExpiry` | no | `3600` | Presigned URL expiry in seconds (*extension*) |

### s3 (S3-compatible stores)

Use this provider for every S3-compatible store (Ceph, KS3, MinIO, Cloudflare R2, ...). Amazon Web
Services itself belongs to the `aws` provider below.

| Parameter | Required | Default | Description |
|---|---|---|---|
| `serverUrl` | yes | — | Service URL, e.g. `http://ceph.internal:7480` |
| `accessKeyId` / `secretAccessKey` | yes | — | Credentials |
| `bucketName` | yes | — | Bucket name |
| `forcePathStyle` | no | `false` | Path-style addressing (applies to the presigner too) |
| `useChunkEncoding` | no | `false` | AWS chunked payload signing |
| `protocol` | no | `1` | `1` = HTTP, `2` = HTTPS (or `http`/`https`); used when `serverUrl` has no scheme |
| `authenticationRegion` | no | `us-east-1` | Region used for AWS Signature Version 4 |
| `createBucketIfNotExists` | no | `false` | Create the bucket on first save |
| `urlExpiry` | no | `3600` | Presigned URL expiry in seconds (*extension*) |

### aws (Amazon Web Services S3 only)

| Parameter | Required | Default | Description |
|---|---|---|---|
| `region` | yes | — | AWS region, e.g. `us-east-1` |
| `containerName` | yes | — | Bucket name |
| `accessKeyId` / `secretAccessKey` | one of | — | Static credentials |
| `useCredentials` | one of | `false` | Use `profileName` or the AWS default provider chain |
| `useTemporaryCredentials` | one of | `false` | Session credentials from STS `GetSessionToken` |
| `useTemporaryFederatedCredentials` | one of | `false` | Credentials from STS `GetFederationToken` |
| `profileName` / `profilesLocation` | no | `""` | AWS profile file or directory |
| `durationSeconds` | no | `0` | Temporary credential validity (service default when `0`) |
| `name` / `policy` | federated mode | — | Federation token name and policy |
| `temporaryCredentialsCacheKey` | no | `<container>/aws` | Process-wide cache key of the temporary credentials |
| `createContainerIfNotExists` | no | `false` | Create the bucket on first save |
| `urlExpiry` | no | `3600` | Presigned URL expiry in seconds (*extension*) |

Exactly one credential mode is used, in this order: `useCredentials`, `useTemporaryCredentials`,
`useTemporaryFederatedCredentials`, static keys. Temporary credentials are cached and refreshed
shortly before they expire.

### ks3 (Kingsoft Cloud KS3)

KS3 is **not** an S3-compatible store: it authenticates with its own `KSS` signature instead of AWS
Signature Version 4, so it has a dedicated provider built on the KS3 Java SDK. Leave
`useAwsSignature` at its default (`false`) unless you talk to an AWS-compatible gateway.

| Parameter | Required | Default | Description |
|---|---|---|---|
| `endpoint` | yes | — | Service host, e.g. `ks3-cn-beijing.ksyuncs.com` (a scheme is accepted) |
| `bucketName` | yes | — | Bucket name |
| `accessKey` / `secretKey` | yes | — | Credentials |
| `protocol` | no | `http` | `http` or `https`; an endpoint scheme wins when it is omitted |
| `userAgent` | no | SDK default | HTTP user agent |
| `maxConnections` | no | SDK default | Connection pool size |
| `timeout` | no | SDK default | Connection timeout in milliseconds |
| `readWriteTimeout` | no | SDK default | Socket read/write timeout in milliseconds |
| `createContainerIfNotExists` | no | `false` | Create the bucket on first save |
| `signerVersion` | no | SDK default (`V2`) | `V2`, `V4` or `V4_UNSIGNED_PAYLOAD_SIGNER` (*extension*) |
| `useAwsSignature` | no | `false` | Use the AWS signature instead of the KS3 one (*extension*) |
| `urlExpiry` | no | `3600` | Presigned URL expiry in seconds (*extension*) |

### azure

| Parameter | Required | Default | Description |
|---|---|---|---|
| `containerName` | yes | — | Blob container name |
| `connectionString` | one of — | — | Account connection string |
| `accountName` + `accountKey` | one of — | — | Explicit credential pair (*extension*) |
| `createContainerIfNotExists` | no | `false` | Create the container on first save |
| `sasExpiry` | no | `3600` | SAS token validity in seconds (*extension*) |

### aliyun-oss

| Parameter | Required | Default | Description |
|---|---|---|---|
| `endpoint` | yes | — | OSS endpoint, e.g. `oss-cn-hangzhou.aliyuncs.com` |
| `accessKeyId` / `accessKeySecret` | yes | — | Credentials |
| `bucketName` | yes | — | Bucket name |
| `regionId` | STS mode | — | Region of the STS call |
| `useSecurityTokenService` | no | `false` | Use STS `AssumeRole` temporary credentials |
| `roleArn` / `roleSessionName` | STS mode | — | Role to assume and session name |
| `durationSeconds` | no | `0` | Temporary credential validity in seconds |
| `policy` | no | `""` | Extra session policy of the temporary credentials |
| `temporaryCredentialsCacheKey` | no | `<container>/aliyun` | Process-wide cache key of the temporary credentials |
| `createContainerIfNotExists` | no | `false` | Create the bucket on first save |
| `urlExpiry` | no | `3600` | Presigned URL expiry in seconds (*extension*) |
| `useInternal` | no | `false` | Rewrite `.aliyuncs.com` endpoints to `-internal` (*extension*) |

### huawei-obs

| Parameter | Required | Default | Description |
|---|---|---|---|
| `endpoint` | yes | — | OBS endpoint |
| `accessKeyId` / `accessKeySecret` | yes | — | Credentials |
| `bucketName` | yes | — | Bucket name |
| `createContainerIfNotExists` | no | `false` | Create the bucket on first save |
| `urlExpiry` | no | `3600` | Signed URL expiry in seconds (*extension*) |

### sftp (Polystore extension — the reference framework has no SFTP provider)

| Parameter | Required | Default | Description |
|---|---|---|---|
| `host` / `username` / `basePath` | yes | — | Server, user and remote root directory |
| `port` | no | `22` | SSH port |
| `password` or `privateKeyPath` | one of — | — | Authentication credentials |
| `urlPrefix` | no | `""` | HTTP prefix returned by `getUrl` |
| `poolSize` | no | `5` | Maximum pooled connections |
| `strictHostKeyChecking` | no | `no` | JSch host key policy |

## Build

```bash
./mvnw clean install          # full build with tests
./mvnw test -pl polystore-core  # single module
```

## License

[MIT](https://opensource.org/licenses/MIT)
