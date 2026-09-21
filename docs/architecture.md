# Polystore — Architecture & Functional Design

> Reference: the C# `SharpAbp.Abp.FileStoring` + `Kayisoft.Abp.FileStoring.*` ecosystem, redesigned
> for Java / Spring Boot.

---

## 1. Design Goals

- **Unified abstraction**: expose a storage-backend-agnostic `StorageClient` API so business code
  never needs to know which storage is underneath.
- **Multiple containers per process**: several storage containers (containers) can be configured at
  the same time, each independently selecting its backend type and connection parameters.
- **Pluggable backends**: backends register through an SPI; adding the corresponding submodule to
  the classpath activates it, leaving it out keeps it unloaded.
- **Spring Boot friendly**: a `spring-boot-starter` submodule enables zero-code adoption driven by
  `application.yml`.
- **Lightweight and non-invasive**: the core abstractions do not depend on Spring and can be used
  manually outside Spring environments.

---

## 2. Overall Architecture

```
┌─────────────────────────────────────────────────────────┐
│                      Business Code                      │
└──────────────────────────┬──────────────────────────────┘
                           │ inject
                           ▼
┌─────────────────────────────────────────────────────────┐
│               StorageManager (entry facade)             │
│   getContainer(name) → StorageContainer                 │
└──────────────────────────┬──────────────────────────────┘
                           │
          ┌────────────────┬────────────────┬────────────────┐
          ▼                ▼                ▼                ▼
   StorageContainer  StorageContainer  StorageContainer  StorageContainer
    (local/images)   (minio/dicom)    (s3/backup)      (aws/archive)
          │                │                │                │
          ▼                ▼                ▼                ▼
   LocalProvider    MinioProvider     S3Provider       AwsProvider
```

### Core Flow

1. At startup the `StorageManager` initializes every container from the configuration; each
   container holds one `StorageProvider` instance.
2. Business code obtains a `StorageContainer` via `storageManager.getContainer("name")`.
3. It invokes operations on the `StorageContainer`: save / get / delete / exists / getUrl, etc.
4. The `StorageContainer` delegates each operation to its `StorageProvider`.

---

## 3. Module Layout

```
polystore/
├── polystore-core                    # core interfaces and abstractions, no Spring dependency
├── polystore-spring-boot-starter     # Spring Boot starter (manager assembly, auto-config, events)
│
├── polystore-local                   # local filesystem backend (reference: FileSystem)
├── polystore-minio                   # MinIO backend (MinIO Java SDK)
├── polystore-s3                      # S3-compatible stores, never Amazon itself (AWS SDK v2)
├── polystore-aws                     # Amazon Web Services S3 only (AWS SDK v2)
├── polystore-ks3                     # Kingsoft Cloud KS3 (KS3 Java SDK, native KSS signature)
├── polystore-azure                   # Azure Blob Storage backend
├── polystore-aliyun-oss              # Alibaba Cloud OSS backend
├── polystore-huawei-obs              # Huawei Cloud OBS backend
└── polystore-sftp                    # SFTP backend (JSch + connection pool)
```

> Module naming follows the sibling repositories (stow, latchq): `{name}-core` +
> `{name}-spring-boot-starter`. The originally drafted `polystore-spring` (Spring integration) and
> `polystore-autoconfigure` (Boot auto-configuration) were merged into a single starter module.

### Dependency Hierarchy

```
polystore-spring-boot-starter → polystore-core
              ↓
      local/minio/...  (each backend depends only on core)
```

---

## 4. Core Interfaces (polystore-core)

### 4.1 StorageClient — Unified Operations API

```java
public interface StorageClient {

    /** Saves a file; the caller closes the inputStream */
    void save(String fileName, InputStream inputStream, SaveArgs args);

    /** Opens the file content; the caller closes the returned stream */
    InputStream get(String fileName);

    /** Deletes a file; missing files are silently ignored */
    void delete(String fileName);

    /** Checks whether the file exists */
    boolean exists(String fileName);

    /** Resolves an accessible URL (object storages presign; local-style backends compose a path) */
    String getUrl(String fileName, UrlArgs args);

    /** Bulk delete */
    void deleteAll(Collection<String> fileNames);
}
```

### 4.2 StorageContainer — Container (configuration + client)

```java
public interface StorageContainer extends StorageClient {

    /** Container name, unique per manager */
    String getName();

    /** Backend provider type identifier, e.g. "local", "minio", "s3" */
    String getProviderType();

    /** Container metadata (read-only view of the provider configuration) */
    ContainerInfo getInfo();
}
```

### 4.3 StorageProvider — Backend SPI

```java
public interface StorageProvider {

    /** Provider type identifier, matching the configuration `type` field, e.g. "minio" */
    String getType();

    /** Creates a StorageContainer instance from the container configuration */
    StorageContainer createContainer(ContainerConfiguration config);
}
```

### 4.4 StorageManager — Global Manager

```java
public interface StorageManager {

    /** Returns the named container; throws ContainerNotFoundException when absent */
    StorageContainer getContainer(String name);

    /** Returns the default container (the one with default: true) */
    StorageContainer getDefaultContainer();

    /** Returns the names of all registered containers */
    Collection<String> containerNames();
}
```

### 4.5 Supporting Types

```java
// Save parameters
public class SaveArgs {
    private String contentType;           // MIME type
    private Map<String, String> metadata; // custom metadata
    private boolean overwrite = true;     // overwrite an existing file with the same name
}

// URL parameters
public class UrlArgs {
    private Duration expiry = Duration.ofHours(1); // presigned URL expiry
    private boolean inline = false;                // Content-Disposition inline
}

// Container configuration (one yml container entry)
public class ContainerConfiguration {
    private String name;
    private String type;          // provider type
    private boolean isDefault;
    private Map<String, Object> properties; // provider-specific parameters
}

// Container info (read-only)
public class ContainerInfo {
    private String name;
    private String providerType;
    private boolean isDefault;
}
```

---

## 5. Backend Design

Parameter names are taken from the reference `SharpAbp.Abp.FileStoring.{Provider}`
`*FileProviderConfigurationNames` constants, so a configuration migrated from the C# framework keeps
its parameter names. Lookups are case- and separator-insensitive, and the qualified
`{Provider}.{Name}` form is accepted as well: `Minio.EndPoint`, `minio.end-point` and `endPoint`
all resolve to the same parameter. Parameters without a reference counterpart are marked as
Polystore extensions.

**Implementation convention.** Every backend owns exactly one immutable, package-private
configuration record — the provider class name with `Provider` replaced by `Configuration`
(`MinioStorageProvider` → `MinioStorageConfiguration`, `S3StorageProvider` →
`S3StorageConfiguration`) — which parses `ContainerConfiguration#getProperties()` in its
`static <Name> from(ContainerConfiguration config)` factory, applies the documented defaults and
validates the required values. Providers, storage clients and SDK factories never read the raw
parameter map, so parameter handling is identical in shape for every backend. SDK client assembly
that carries real logic (AWS credentials/STS, Aliyun STS, the KS3 signer configuration) lives in a
package-private `XxxFactory` that consumes the configuration record.

Object storage providers separate *Amazon* from *S3-compatible* stores, mirroring the reference
`Aws` and `S3` providers: `aws` never overrides the endpoint and targets AWS regions, while `s3`
always talks to the configured `serverUrl`. `createContainerIfNotExists` /
`createBucketIfNotExists` is honoured **lazily on the first save** (as in the reference), never at
container construction, so building a container stays network-free.

A provider may also declare **aliases** (`StorageProvider#getAliases()`) so a configuration written
against another naming scheme keeps working: `local` answers to `FileSystem`, `aliyun-oss` to
`Aliyun`, `huawei-obs` to `Obs` and `ks3` to `KS3`. Types and aliases are matched
case-insensitively, and a canonical type always wins over another provider's alias.

KS3 is deliberately **not** modelled as an S3-compatible store: Kingsoft Cloud KS3 authenticates
with its own `KSS` signature rather than AWS Signature Version 4, so it has a dedicated provider
built on the KS3 Java SDK (the SDK keeps `useAwsSignature = false` and signs with `Ks3V2Signer` by
default).

### 5.1 Local (local filesystem, reference: FileSystem)

| Parameter | Description | Default |
|------|------|--------|
| `basePath` | Storage root directory | required |
| `appendContainerNameToBasePath` | Store under `{basePath}/{containerName}` | `true` |
| `httpServer` | URL prefix (HTTP static-resource address) | `""` |
| `createDirectories` | Create missing subdirectories on save (extension) | `true` |

- `getUrl` returns `httpServer` + the stored relative path; nothing is presigned.
- Suitable for development environments and intranets without object storage.

### 5.2 MinIO

| Parameter | Description | Default |
|------|------|--------|
| `endPoint` | MinIO service address (scheme optional) | required |
| `accessKey` | Access Key | required |
| `secretKey` | Secret Key | required |
| `bucketName` | Bucket name | required |
| `withSSL` | Use HTTPS when the endpoint carries no scheme | `false` |
| `createBucketIfNotExists` | Create the bucket on the first save | `false` |
| `region` | Signing region (extension) | `us-east-1` |
| `urlExpiry` | Presigned URL expiry (seconds, extension) | `3600` |

- Built on the `io.minio:minio` SDK.
- Setting `region` keeps `getUrl` fully local; without it the SDK would query the bucket location.

### 5.3 S3-compatible stores

| Parameter | Description | Default |
|------|------|--------|
| `serverUrl` | Service URL (Ceph, KS3, MinIO, R2, ...) | required |
| `accessKeyId` | Access Key ID | required |
| `secretAccessKey` | Secret Access Key | required |
| `bucketName` | Bucket name | required |
| `forcePathStyle` | Force path-style addressing | `false` |
| `useChunkEncoding` | AWS chunked payload signing | `false` |
| `protocol` | `1` = HTTP, `2` = HTTPS (used when `serverUrl` has no scheme) | `1` |
| `authenticationRegion` | Region used for AWS Signature Version 4 | `us-east-1` |
| `createBucketIfNotExists` | Create the bucket on the first save | `false` |
| `urlExpiry` | Presigned URL expiry (seconds, extension) | `3600` |

- Built on AWS SDK for Java v2 (`software.amazon.awssdk`), always with an endpoint override.

### 5.4 AWS (Amazon Web Services only)

| Parameter | Description | Default |
|------|------|--------|
| `region` | AWS region | required |
| `containerName` | Bucket name | required |
| `accessKeyId` / `secretAccessKey` | Static credentials | one credential mode |
| `useCredentials` | Use `profileName` or the AWS default provider chain | `false` |
| `useTemporaryCredentials` | Session credentials from STS `GetSessionToken` | `false` |
| `useTemporaryFederatedCredentials` | Credentials from STS `GetFederationToken` | `false` |
| `profileName` / `profilesLocation` | AWS profile selection | `""` |
| `durationSeconds` | Temporary credential validity | service default |
| `name` / `policy` | Federation token name and policy | required in federated mode |
| `temporaryCredentialsCacheKey` | Process-wide cache key of temporary credentials | `<container>/aws` |
| `createContainerIfNotExists` | Create the bucket on the first save | `false` |
| `urlExpiry` | Presigned URL expiry (seconds, extension) | `3600` |

- The credential mode is selected in the reference order: `useCredentials`,
  `useTemporaryCredentials`, `useTemporaryFederatedCredentials`, then static keys.
- Temporary credentials are cached process-wide and refreshed shortly before expiry.

### 5.5 KS3 (Kingsoft Cloud)

| Parameter | Description | Default |
|------|------|--------|
| `endpoint` | KS3 service host, e.g. `ks3-cn-beijing.ksyuncs.com` (scheme optional) | required |
| `bucketName` | Bucket name | required |
| `accessKey` / `secretKey` | Credentials | required |
| `protocol` | `http` or `https` (an endpoint scheme wins when omitted) | `http` |
| `userAgent` | HTTP user agent | SDK default |
| `maxConnections` | Connection pool size | SDK default |
| `timeout` | Connection timeout (milliseconds) | SDK default |
| `readWriteTimeout` | Socket read/write timeout (milliseconds) | SDK default |
| `createContainerIfNotExists` | Create the bucket on the first save | `false` |
| `signerVersion` | KS3 signer `V2`, `V4` or `V4_UNSIGNED_PAYLOAD_SIGNER` (extension) | `V2` |
| `useAwsSignature` | Use the AWS signature instead of the KS3 native one (extension) | `false` |
| `urlExpiry` | Presigned URL expiry (seconds, extension) | `3600` |

- Built on `com.ksyun:ks3-kss-java-sdk`; requests are signed with the KS3 native `KSS` signature,
  which is not AWS Signature Version 4. `useAwsSignature` exists only for AWS-compatible gateways.

### 5.6 Azure Blob Storage

| Parameter | Description | Default |
|------|------|--------|
| `connectionString` | Storage account connection string | one of the two credential forms |
| `accountName` | Storage account name (extension) | — |
| `accountKey` | Storage account key (extension) | — |
| `containerName` | Container name | required |
| `createContainerIfNotExists` | Create the container on the first save | `false` |
| `sasExpiry` | SAS token expiry (seconds, extension) | `3600` |

- Built on `com.azure:azure-storage-blob`.

### 5.7 Alibaba Cloud OSS

| Parameter | Description | Default |
|------|------|--------|
| `endpoint` | OSS endpoint | required |
| `accessKeyId` | Access Key ID | required |
| `accessKeySecret` | Access Key Secret | required |
| `bucketName` | Bucket name | required |
| `regionId` | Region of the STS call | required in STS mode |
| `useSecurityTokenService` | Use STS `AssumeRole` temporary credentials | `false` |
| `roleArn` / `roleSessionName` | Role to assume and session name | required in STS mode |
| `durationSeconds` | Temporary credential validity (seconds) | service default |
| `policy` | Extra session policy | `""` |
| `temporaryCredentialsCacheKey` | Process-wide cache key of temporary credentials | `<container>/aliyun` |
| `createContainerIfNotExists` | Create the bucket on the first save | `false` |
| `urlExpiry` | Presigned URL expiry (seconds, extension) | `3600` |
| `useInternal` | Rewrite the endpoint to the intranet variant (extension) | `false` |

- Built on `com.aliyun.oss:aliyun-sdk-oss`; STS uses `com.aliyun:aliyun-java-sdk-core`.

### 5.8 Huawei Cloud OBS

| Parameter | Description | Default |
|------|------|--------|
| `endpoint` | OBS endpoint | required |
| `accessKeyId` | Access Key ID | required |
| `accessKeySecret` | Access Key Secret | required |
| `bucketName` | Bucket name | required |
| `createContainerIfNotExists` | Create the bucket on the first save | `false` |
| `urlExpiry` | Signed URL expiry (seconds, extension) | `3600` |

- Built on `com.huaweicloud:esdk-obs-java`.

### 5.9 FastDFS (not implemented)

- The only maintained third-party driver, `com.github.tobato:fastdfs-client`, wires its internals
  via Spring field injection and cannot be assembled cleanly without Spring; additionally the
  FastDFS protocol cannot address files by name (ids are generated server-side), which does not
  match the name-based `StorageClient` semantics. Per the rule "no backend without a cleanly
  usable third-party driver", this backend is deferred until a suitable driver appears or a
  Spring-coupled approach is accepted.

### 5.10 SFTP

| Parameter | Description | Default |
|------|------|--------|
| `host` | SFTP host | required |
| `port` | Port | `22` |
| `username` | User name | required |
| `password` | Password | `""` |
| `privateKeyPath` | Private key file path (one of the two credential forms) | `""` |
| `basePath` | Remote root directory | required |
| `urlPrefix` | File URL prefix | `""` |
| `poolSize` | Connection pool size | `5` |

- Built on `com.github.mwiede:jsch` (the maintained JSch fork).
- Connections are pooled so SSH sessions are not re-established per operation.

---

## 6. Multi-Tenancy

### 6.1 Scope and Boundaries

The C# counterpart relies on the full ABP tenancy module (`ICurrentTenant`, tenant switching,
tenant lifecycle management, ...). Polystore **does not introduce any tenant management
framework**; it solves exactly one problem:

> Files of different tenants inside the same storage container (the same bucket) are physically
> separated by a path prefix.

The C# `AppendTenantToPath=true` switch is precisely this capability, implemented here as a
lightweight per-container setting.

### 6.2 Tenant Id Sources

Polystore's entire tenancy surface is one minimal interface:

```java
@FunctionalInterface
public interface TenantIdSupplier {
    /** Returns the current tenant id; null when the application is not multi-tenant */
    String get();
}
```

**Primary path (implicit)**: the application registers a `TenantIdSupplier` once; afterwards no
call site needs to pass a tenant id — the framework reads it automatically:

```java
// register once, plugging into your tenant context
storageManager = PolystoreBuilder.builder()
    .tenantIdSupplier(() -> MyTenantContext.currentTenantId())
    .build();

// call sites stay clean, no boilerplate
container.save("photo.jpg", stream, SaveArgs.defaults());
```

**Override path (explicit)**: `SaveArgs` accepts an explicit `tenantId` that takes priority over
the `TenantIdSupplier` — useful for background batch jobs, cross-tenant administration and unit
tests:

```java
container.save("photo.jpg", stream, SaveArgs.builder()
    .tenantId("tenant-abc")
    .build());
```

**Resolution priority**:

```
SaveArgs.tenantId (explicit) > TenantIdSupplier (implicit) > null
```

Without a registered `TenantIdSupplier` the resolved id defaults to `null`. Under `PATH_PREFIX`
a final `null` raises `TenantIdMissingException`.

### 6.3 Per-Container Isolation Switch

Each container independently enables path isolation, off by default:

```java
public enum TenantIsolationMode {
    NONE,        // all tenants share the same path space (default)
    PATH_PREFIX  // file paths are transparently prefixed with {tenantId}/
}
```

### 6.4 Path Translation Rules

With `tenantIsolation = PATH_PREFIX`, every operation transparently adds the prefix:

| Caller passes | Actual path operated on |
|-----------|-------------|
| `images/photo.jpg` | `{tenantId}/images/photo.jpg` |
| `2024/01/abc.dcm` | `{tenantId}/2024/01/abc.dcm` |

The tenant id resolves by priority: `SaveArgs.tenantId` > `TenantIdSupplier` > null. When the
final id is null and the container uses `PATH_PREFIX`, `TenantIdMissingException` is thrown.

### 6.5 yml Example

```yaml
polystore:
  containers:
    - name: dicom
      type: minio
      tenant-isolation: PATH_PREFIX   # enable path isolation
      minio:
        end-point: minio.internal:9000
        access-key: admin
        secret-key: password
        bucket-name: dicom

    - name: public-assets
      type: local
      tenant-isolation: NONE          # shared assets, no isolation (default, may be omitted)
      local:
        base-path: /data/public
        append-container-name-to-base-path: false
        http-server: https://cdn.example.com
```

---

## 7. Configuration (Spring Boot)

```yaml
polystore:
  containers:
    - name: images
      type: local
      default: true
      local:
        base-path: /data/files
        http-server: https://cdn.example.com/images

    - name: dicom
      type: minio
      minio:
        end-point: minio.internal:9000
        access-key: admin
        secret-key: password
        bucket-name: dicom
        with-ssl: true
        create-bucket-if-not-exists: true

    - name: ceph-backup
      type: s3
      s3:
        server-url: http://ceph.internal:7480
        access-key-id: AKIAXXXXXXXX
        secret-access-key: xxxxxxxx
        bucket-name: my-backup
        force-path-style: true

    - name: amazon-archive
      type: aws
      aws:
        region: us-east-1
        container-name: my-bucket
        use-credentials: true
        profile-name: production

    - name: archive
      type: aliyun-oss
      aliyun-oss:
        endpoint: oss-cn-hangzhou.aliyuncs.com
        access-key-id: LTAIxxxxxxxx
        access-key-secret: xxxxxxxx
        bucket-name: my-archive

    - name: ks3-archive
      type: ks3
      ks3:
        endpoint: ks3-cn-beijing.ksyuncs.com
        bucket-name: my-ks3-bucket
        access-key: AKLTxxxxxxxx
        secret-key: xxxxxxxx
        protocol: https
```

---

## 8. Exception Hierarchy

```
PolystoreException (base)
├── ContainerNotFoundException                 # container name not registered
├── StorageProviderNotFoundException           # provider type not registered
├── StorageFileNotFoundException               # file not found
├── StorageFileAlreadyExistsException          # file exists and overwrite=false
├── TenantIdMissingException                   # PATH_PREFIX without a resolvable tenant id
└── StorageOperationException                  # backend I/O failure (carries the cause)
```

The file-related exceptions carry the `Storage` prefix because their plain names clash with
`java.io.FileNotFoundException` and `java.nio.file.FileAlreadyExistsException`.

---

## 9. Extension Points

### 9.1 Custom Providers

Implement the `StorageProvider` interface and register the implementation as a Spring bean; the
framework discovers and registers it automatically:

```java
@Component
public class MyCustomProvider implements StorageProvider {
    @Override public String getType() { return "my-custom"; }
    @Override public StorageContainer createContainer(ContainerConfiguration config) { ... }
}
```

Backends that must stay Spring-free register through `ServiceLoader` instead
(`META-INF/services/io.github.cocosip.polystore.StorageProvider`); beans take precedence on a
type clash.

### 9.2 Operation Events

After successful saves and deletes the starter publishes Spring `ApplicationEvent`s for audit
logging, cache invalidation and similar concerns:

- `FileSavedEvent`
- `FileDeletedEvent`

---

## 10. Build Tooling

**This repository uses the Maven Wrapper; never rely on a system-wide Maven.**

All build commands go through `mvnw` (Linux/macOS) or `mvnw.cmd` (Windows) at the repository
root:

```bash
# build
./mvnw clean install

# skip tests
./mvnw clean install -DskipTests

# run a single module's tests
./mvnw test -pl polystore-core
```

Wrapper layout:

```
polystore/
├── .mvn/
│   └── wrapper/
│       └── maven-wrapper.properties   # pins the Maven version and download URL
├── mvnw                               # Unix launcher
└── mvnw.cmd                           # Windows launcher
```

The Maven version is pinned in `maven-wrapper.properties` (3.9.16) so every developer and CI uses
the same build, independent of local environments.

---

## 11. Maven Dependency Guide

| Scenario | Dependencies |
|------|---------|
| Core interfaces only (no Spring) | `polystore-core` |
| Spring Boot application with yml configuration | `polystore-spring-boot-starter` + the backend modules |

---

## 12. Future Plans (out of v1 scope)

- **Multipart upload**: chunked uploads for large files against the S3 / MinIO multipart APIs.
- **File id generator**: timestamp / template based path generation strategies, referencing
  `Kayisoft.Abp.FileStoring.FileIds`.
- **Mirror sync**: write-through synchronization to multiple containers (primary/backup).
- **CDN signed URLs**: signed CDN addresses for CloudFront, Alibaba Cloud CDN, etc.
