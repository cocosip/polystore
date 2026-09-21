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
| `polystore-s3` | AWS S3 / S3-compatible backend (AWS SDK v2) |
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
        base-path: /data/files/images
        url-prefix: https://cdn.example.com/images

    - name: dicom
      type: minio
      tenant-isolation: PATH_PREFIX
      minio:
        endpoint: http://minio.internal:9000
        access-key: admin
        secret-key: password
        bucket-name: dicom
        create-bucket-if-absent: true
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
container's `type`. Parameters are matched case- and separator-insensitively, so camelCase and
kebab-case both work.

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

### local

| Parameter | Required | Default | Description |
|---|---|---|---|
| `basePath` | yes | — | Storage root directory |
| `urlPrefix` | no | `""` | HTTP prefix returned by `getUrl` |
| `createDirectories` | no | `true` | Create missing directories on save |

### minio

| Parameter | Required | Default | Description |
|---|---|---|---|
| `endpoint` | yes | — | MinIO service address |
| `accessKey` / `secretKey` | yes | — | Credentials |
| `bucketName` | yes | — | Bucket name |
| `region` | no | `""` | Region |
| `secure` | no | `false` | Force HTTPS when the endpoint has no scheme |
| `urlExpiry` | no | `3600` | Presigned URL expiry (seconds) |
| `createBucketIfAbsent` | no | `false` | Create the bucket at startup (network call) |

### s3 (AWS SDK v2, S3-compatible)

| Parameter | Required | Default | Description |
|---|---|---|---|
| `region` | yes | — | Region |
| `accessKeyId` / `secretAccessKey` | yes | — | Credentials |
| `bucketName` | yes | — | Bucket name |
| `endpoint` | no | `""` | Override for Ceph, KS3 and other compatible stores |
| `pathStyleAccess` | no | `false` | Path-style addressing (applies to presigner too) |
| `urlExpiry` | no | `3600` | Presigned URL expiry (seconds) |

### azure

| Parameter | Required | Default | Description |
|---|---|---|---|
| `containerName` | yes | — | Blob container name |
| `connectionString` | one of — | — | Account connection string |
| `accountName` + `accountKey` | one of — | — | Or the explicit credential pair |
| `sasExpiry` | no | `3600` | SAS token validity (seconds) |

### aliyun-oss

| Parameter | Required | Default | Description |
|---|---|---|---|
| `endpoint` | yes | — | OSS endpoint, e.g. `oss-cn-hangzhou.aliyuncs.com` |
| `accessKeyId` / `accessKeySecret` | yes | — | Credentials |
| `bucketName` | yes | — | Bucket name |
| `urlExpiry` | no | `3600` | Presigned URL expiry (seconds) |
| `useInternal` | no | `false` | Rewrite `.aliyuncs.com` endpoints to `-internal` |

### huawei-obs

| Parameter | Required | Default | Description |
|---|---|---|---|
| `endpoint` | yes | — | OBS endpoint |
| `accessKey` / `secretKey` | yes | — | Credentials |
| `bucketName` | yes | — | Bucket name |
| `urlExpiry` | no | `3600` | Signed URL expiry (seconds) |

### sftp

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
