# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Core abstractions: `StorageClient`, `StorageContainer`, `StorageProvider` SPI,
  `StorageManager`, `SaveArgs` / `UrlArgs`, `ContainerConfiguration` / `ContainerInfo`,
  `TenantIdSupplier` / `TenantIsolationMode` and the `PolystoreException` hierarchy.
- Spring Boot starter: `polystore.*` yml binding, `DefaultStorageManager`, tenant `PATH_PREFIX`
  isolation, provider discovery (Spring beans plus `ServiceLoader`), `FileSavedEvent` /
  `FileDeletedEvent`, auto-configuration with IDE configuration metadata, and `PolystoreBuilder`
  for Spring-free assembly.
- Storage backends: local filesystem, MinIO, AWS S3 / S3-compatible, Azure Blob, Aliyun OSS,
  Huawei Cloud OBS and SFTP (JSch connection pool).
- Build tooling: Maven Wrapper (3.9.16), spotless (palantir format + sortPom), spotbugs, jacoco,
  enforcer, javadoc/source attachment and a Sonatype Central release profile.
