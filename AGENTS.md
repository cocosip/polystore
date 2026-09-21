# AGENTS.md

Instructions for coding agents working in this repository.

## Project Overview

Polystore is a pluggable file storage abstraction for Java 21. Business code programs against one
unified `StorageClient` API; a process hosts multiple named containers, each selecting a backend
through an SPI. Modules: `polystore-core` (framework-free), `polystore-spring-boot-starter`
(yml binding, manager assembly, tenant isolation, events, auto-configuration) and one module per
backend: `polystore-local` (reference provider: `FileSystem`), `polystore-minio`, `polystore-s3`
(S3-compatible stores only), `polystore-aws` (Amazon Web Services only), `polystore-ks3`
(Kingsoft Cloud KS3, native `KSS` signature), `polystore-azure`, `polystore-aliyun-oss`,
`polystore-huawei-obs`, `polystore-sftp`. See
[`docs/architecture.md`](docs/architecture.md) for the full design.

## Build and Test

The Maven Wrapper (3.9.16) is mandatory; never call a system-wide `mvn`.

```bash
./mvnw clean install            # full build: tests + spotless + spotbugs + javadoc gates
./mvnw test -pl polystore-core  # single module tests
./mvnw -q spotless:apply        # fix formatting before committing (palantir format + sortPom)
```

Java 21 (enforcer range `[21,22)`) and Maven `[3.9,4)`. `verify` is the CI gate — it runs tests,
formatting checks, spotbugs and javadoc generation; a build is not done until `clean install`
passes with every test green.

## Code Conventions

- Formatting is enforced by spotless (palantir Java Format 4-space style, sorted imports,
  sorted POMs). Run `spotless:apply` before committing; never hand-format.
- Public classes and methods carry Javadoc in English. Javadoc references to exception classes
  require a proper import — the exception hierarchy lives in
  `io.github.cocosip.polystore.exception`, not the root package.
- File-related exceptions keep the `Storage` prefix (`StorageFileNotFoundException`,
  `StorageFileAlreadyExistsException`) because the plain names clash with `java.io` /
  `java.nio.file`.
- Argument types (`StorageSaveOptions`, `StorageProvider*Args`,
  `ContainerConfiguration`) are immutable: builder or validated constructors, defensive copies,
  read-only maps. The caller-owned `InputStream` is the one intentional shared reference.
- Spotbugs is a verify gate. When wrapping an SDK client or exposing a binder list triggers
  `EI_EXPOSE_REP`/`EI_EXPOSE_REP2`, suppress with `@SuppressFBWarnings` plus a `justification`
  explaining why the sharing is intentional — never suppress silently.
- Dependencies and plugin versions are managed centrally in the parent POM properties; never pin
  a version inside a submodule when the parent manages it.

## Backend Module Rules

- A backend module depends only on `polystore-core` (+ its third-party SDK). It must never depend
  on Spring or on the starter.
- Register the provider via `META-INF/services/io.github.cocosip.polystore.StorageProvider`
  (ServiceLoader) so backend modules stay framework-free; the starter merges ServiceLoader
  entries with Spring beans (beans win on a type clash).
- Provider types are matched case-insensitively. When the reference provider name differs from the
  Polystore type id, declare it through `StorageProvider#getAliases()` (e.g. `FileSystem` for
  `local`, `Aliyun` for `aliyun-oss`, `Obs` for `huawei-obs`) so migrated configurations resolve.
- Read provider parameters through `io.github.cocosip.polystore.util.ConfigUtils`; keys are
  matched case-insensitively and separator-insensitively, so yml `access-key` and programmatic
  `accessKey` both resolve, and the qualified `Minio.EndPoint` form of the reference framework
  matches `endPoint` as well. Required parameters raise `IllegalStateException`, not a storage
  exception.
- Every backend defines exactly one immutable configuration record, named after its provider class
  with the `Provider` suffix replaced by `Configuration` (`MinioStorageProvider` →
  `MinioStorageConfiguration`, `S3StorageProvider` → `S3StorageConfiguration`). It is
  package-private, carries every provider parameter together with its default, and parses them in a
  single `static <Name> from(ContainerConfiguration config)` factory that also validates required
  and malformed values (`IllegalStateException`). `ConfigUtils` and the raw
  `ContainerConfiguration#getProperties()` map may only be touched inside that factory — providers,
  storage clients and SDK factories must go through the configuration record.
- Parameter names follow the reference `SharpAbp.Abp.FileStoring.{Provider}`
  `*FileProviderConfigurationNames` constants (`endPoint`, `withSSL`,
  `createBucketIfNotExists`, `serverUrl`, `forcePathStyle`, `authenticationRegion`,
  `containerName`, `createContainerIfNotExists`, ...); Polystore-only parameters must be
  documented as extensions. `aws` targets Amazon Web Services only and never overrides the
  endpoint; `s3` serves S3-compatible stores and always uses the configured `serverUrl`; `ks3` is
  not S3-compatible (KS3 authenticates with its own `KSS` signature) and therefore uses the KS3
  SDK and its native signer.
  `create*IfNotExists` creates the bucket/container lazily on the first save, so container
  construction never touches the network.
- Public stream saves follow the SharpAbp parameter order with the Java-only
  `InputStream + contentLength` pair. `contentLength` is the exact remaining byte count;
  `InputStream.available()` is never a file length. Backends implement `StorageBackend` and
  receive immutable `StorageProvider*Args` from `DefaultStorageContainer`; a
  `StorageProvider` only parses configuration and creates the backend.
- Object-storage backends automatically select native multipart upload when
  `enableAutoMultiPartUpload` is true and the declared length is greater than
  `multiPartUploadMinFileSize`. Use `multiPartUploadShardingSize` for parts, keep completion tags
  ordered, abort best-effort after initiation failures, preserve the original exception, never
  close the caller stream and never buffer the complete object with `readAllBytes()`.
- Do not ship a backend without a cleanly usable third-party driver. If the only driver requires
  hacks (reflective assembly, protocol-level workarounds), drop the backend — see the FastDFS
  decision in [`docs/development-plan.md`](docs/development-plan.md).
- Tests must be deterministic and offline: presigned/SAS URLs are computed locally by the SDKs and
  can be asserted; use fakes or temp directories for the rest. Do not require network access or
  Docker in unit tests.

## Documentation

- All documentation (`README.md`, `docs/`, AGENTS.md) is written in English.
  Keep it current when behavior changes.
- Check off tasks in [`docs/development-plan.md`](docs/development-plan.md) only after the work
  is verified, and append dated implementation notes for decisions that diverge from the original
  design.

