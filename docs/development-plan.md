# Polystore — Development Plan

> Checked boxes mark completed items. A section title is checked only once all of its subtasks
> are done.

---

## Phase 1: Project Skeleton ✅

- [x] Initialize the Maven multi-module parent POM
- [x] Set up the Maven Wrapper (pinned version, 3.9.16 everywhere)
- [x] Create the submodule directories and placeholder `pom.xml` files
- [x] Unify the compile level (Java 21), encoding and plugin version management

> 2026-09-21: module structure and version scheme aligned with stow / latchq — modules follow the
> `{name}-core` + `{name}-spring-boot-starter` pattern; dependency and plugin versions follow
> stow's set (Spring Boot 3.5.6, JUnit 5.13.4, compiler 3.14.1, jacoco 0.8.13, spotless 2.46.1
> with palantir format + sortPom, spotbugs 4.9.8.1, flatten 1.7.3, enforcer 3.6.2, ...) plus
> release metadata (scm / license / Central publishing profile).

---

## Phase 2: polystore-core ✅

- [x] `StorageClient` interface
- [x] `StorageContainer` interface
- [x] `StorageProvider` interface (SPI)
- [x] `StorageManager` interface
- [x] `SaveArgs` / `UrlArgs` supporting types
- [x] `ContainerConfiguration` / `ContainerInfo`
- [x] `TenantIdSupplier` interface
- [x] `TenantIsolationMode` enum
- [x] Exception hierarchy (`PolystoreException` and subclasses)
- [x] Unit tests (core logic coverage)

> 2026-09-21: 24 unit tests green. Notes: package `io.github.cocosip.polystore`, exceptions in the
> `exception` subpackage; `SaveArgs` / `ContainerConfiguration` are immutable (builder +
> defensive copies + read-only maps); convenience overloads `save(fileName, stream)` and
> `getUrl(fileName)` apply default arguments. The file exceptions are named
> `StorageFileNotFoundException` / `StorageFileAlreadyExistsException` because their plain names
> clash with `java.io` / `java.nio.file` classes.

---

## Phase 3: polystore-spring-boot-starter ✅

- [x] `PolystoreProperties` configuration properties (yml binding)
- [x] `DefaultStorageManager` implementation (provider registration, container initialization)
- [x] Tenant path-prefix interception (`TenantIsolationMode.PATH_PREFIX`)
- [x] `StorageProvider` auto-discovery (Spring bean scanning)
- [x] `PolystoreAutoConfiguration` + `AutoConfiguration.imports` registration
- [x] `FileSavedEvent` / `FileDeletedEvent` publishing
- [x] Configuration metadata (`additional-spring-configuration-metadata.json` for IDE support)
- [x] Unit tests (including Spring Boot context startup verification)

> 2026-09-21: 59 unit tests green (core 28 + starter 31). Notes: core gained
> `DefaultStorageContainer` (backends depend only on core, so the concrete container must live
> there for provider reuse); providers are discovered through two channels (Spring beans first,
> backend modules via `ServiceLoader`, neither depending on Spring); provider-specific yml
> parameters are grouped under a key named after the `type` (e.g. a `minio:` block) and
> `ContainerConfigurationFactory` merges them into `ContainerConfiguration.properties` via the
> Binder API (keys are preserved as written; backends normalize when reading); the default
> container binds from the `default` key; decorator order is raw → tenant prefix → events
> (events carry the caller's logical file name, without the tenant prefix); `PolystoreBuilder`
> covers Spring-free assembly.

---

## Phase 4: Storage Backends ✅

> 2026-09-21: 7 backends implemented and committed separately, 113 unit tests green. FastDFS was
> removed by decision (see "Deferred"). Every backend registers through `ServiceLoader`
> (`META-INF/services`) and depends only on core; the deterministically testable offline logic
> (configuration parsing, client assembly, local computation of presigned/SAS/signed URLs, path
> and tenant semantics) is covered by tests. Testcontainers integration tests for minio / s3 are
> pending a Docker environment. `save` buffers the stream in the S3 / OSS / Azure backends to get
> a deterministic content length; chunked upload for large files is a future plan.

### 4.1 polystore-local ✅
- [x] `LocalStorageProvider` implementation
- [x] Automatic subdirectory creation
- [x] Unit tests

### 4.2 polystore-minio ✅
- [x] `MinioStorageProvider` implementation
- [x] Optional automatic bucket creation (`createBucketIfAbsent`, renamed to
      `createBucketIfNotExists` in Phase 6)
- [x] Presigned URL generation (computed offline)
- [x] Unit tests (offline parts: configuration parsing / presign output; Testcontainers
      integration pending a Docker environment)

### 4.3 polystore-s3 ✅
- [x] `S3StorageProvider` implementation (AWS SDK v2)
- [x] S3-compatible endpoint support (pathStyleAccess, applied to client and presigner alike;
      split into the dedicated `s3` provider with `serverUrl`/`forcePathStyle` in Phase 6)
- [x] Presigned URL generation (computed offline)
- [x] Unit tests (offline parts: virtual-host / path-style presigning, parameter validation;
      Testcontainers LocalStack pending a Docker environment)

### 4.4 polystore-aliyun-oss ✅
- [x] `AliyunOssStorageProvider` implementation
- [x] Presigned URL generation (computed offline)
- [x] Unit tests

### 4.5 polystore-azure ✅
- [x] `AzureBlobStorageProvider` implementation
- [x] SAS token URL generation (signed offline)
- [x] Unit tests

### 4.6 polystore-huawei-obs ✅
- [x] `HuaweiObsStorageProvider` implementation
- [x] Presigned URL generation (`createSignedUrl`, signed offline)
- [x] Unit tests

### 4.7 polystore-sftp ✅
- [x] `SftpStorageProvider` implementation
- [x] JSch connection pool (fixed size, lazily created, borrowed and returned per operation)
- [x] Unit tests (in-memory fake channel covering lease reuse and file semantics)

---

## Phase 6: Reference Configuration Alignment ✅

- [x] Provider parameter names aligned with the SharpAbp `*FileProviderConfigurationNames` constants
- [x] Split Amazon from S3-compatible storage: new `polystore-aws` module, `polystore-s3` rewritten
      as S3-compatible only
- [x] AWS credential modes (`useCredentials` / `useTemporaryCredentials` /
      `useTemporaryFederatedCredentials` / static keys) with an STS-backed process-wide cache
- [x] Alibaba Cloud STS (`useSecurityTokenService` + AssumeRole) with a process-wide cache
- [x] Lazy `createContainerIfNotExists` / `createBucketIfNotExists` on the first save for every
      object store (container construction stays network-free)
- [x] `local` aligned with the reference `FileSystem` provider
      (`appendContainerNameToBasePath`, `httpServer`)
- [x] Case- and separator-insensitive provider `type` / section resolution, plus the qualified
      `{Provider}.{Name}` key form
- [x] Provider aliases for reference names that differ from the Polystore type ids
      (`FileSystem` → `local`, `Aliyun` → `aliyun-oss`, `Obs` → `huawei-obs`, `KS3` → `ks3`)
- [x] New `polystore-ks3` backend using the Kingsoft Cloud KS3 `KSS` signature (KS3 Java SDK),
      explicitly *not* modelled as an S3-compatible store
- [x] One configuration record per backend (`<Provider>StorageConfiguration`) parsed by a single
      `from(ContainerConfiguration)` factory; `ConfigUtils` is confined to those factories, so every
      provider handles its parameters through the same shape
- [x] README, architecture and per-provider Javadoc updated; full `clean verify` green

> 2026-09-21: the provider generation had drifted from the reference framework: each backend read
> its own ad-hoc parameter names (`secure`, `createBucketIfAbsent`, `pathStyleAccess`, `endpoint`,
> `accessKey`/`secretKey`) and the single `s3` module tried to serve both Amazon and every
> S3-compatible store. This phase re-aligns every backend with the reference
> `*FileProviderConfigurationNames` leaves (extensions are marked as such), removes the ambiguity
> between `Aws` and `S3` by adding a dedicated Amazon-only `polystore-aws` module, and moves
> bucket/container creation to the first save, exactly like the reference providers do. Breaking
> the pre-1.0 yml shape was accepted: parameters are `endPoint`/`accessKey`/`secretKey`/`withSSL`/
> `createBucketIfNotExists` (minio), `serverUrl`/`forcePathStyle`/`useChunkEncoding`/`protocol`/
> `authenticationRegion`/`createBucketIfNotExists` (s3), `region`/`containerName`/credential modes
> (aws), `accessKeyId`/`accessKeySecret`/`createContainerIfNotExists` (obs, aliyun, azure) and
> `basePath`/`appendContainerNameToBasePath`/`httpServer` (local). SharpAbp's `KS3` provider was
> added as a dedicated backend as well: KS3 does not implement AWS Signature Version 4 but signs
> with its own `KSS` algorithm, so `polystore-ks3` uses the KS3 Java SDK with
> `useAwsSignature = false` (SDK default, signer version `V2`) and exposes `signerVersion` /
> `useAwsSignature` only as documented extensions. Two deliberate divergences:
> MinIO defaults `region` to `us-east-1` so presigning stays local (the Java SDK would otherwise
> query the bucket location over the network), and `local` keeps `createDirectories` as an extra
> extension. `aws`/`s3`/`minio`/`azure`/`aliyun-oss`/`huawei-obs` tests remain deterministic and
> offline (presigned/SAS/signed URLs, profile-based credential resolution, network-free
> construction); Testcontainers integration for the object stores is still pending a Docker
> environment.

---

## Phase 5: Quality & Release ✅

- [x] Javadoc across all modules (the build runs a javadoc generation gate; public API fully
      covered)
- [x] README (project overview, quick start, per-backend configuration guide)
- [x] CHANGELOG initialized (Keep a Changelog format) — later removed, see Deferred notes
- [x] Maven release configuration (`distributionManagement` / Sonatype Central + `release` profile
      with GPG signing, in place since the skeleton phase)
- [x] CI pipeline (GitHub Actions: Ubuntu + Windows matrix, Temurin JDK 21, `mvnw verify`)

---

## Deferred (future plans)

- **FastDFS backend** (removed 2026-09-21): the only maintained third-party driver, tobato
  fastdfs-client, wires its internals via Spring field injection, which would require reflective
  assembly outside Spring (unacceptable); FastDFS also cannot address files by name, clashing with
  the name-based `StorageClient` semantics. Re-evaluate when a clean driver appears or a
  Spring-coupled approach is accepted (historical implementation: commit `068dd80`).
- **CHANGELOG.md** (removed 2026-09-21): the Keep a Changelog file was deleted from the
  repository.
- Multipart upload
- File id generator (timestamp / template based path generation)
- Mirror sync (write-through to multiple containers)
- CDN signed URLs (CloudFront, Alibaba Cloud CDN)
