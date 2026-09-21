# Polystore — 开发计划

> 勾选表示该项已完成。模块内各子任务全部完成后，再勾选模块标题。

---

## 阶段一：项目骨架

- [ ] 初始化 Maven 多模块父 POM
- [ ] 配置 Maven Wrapper（固定版本）
- [ ] 各子模块目录与 `pom.xml` 占位创建
- [ ] 配置统一的编译版本（Java 17+）、编码、插件版本管理

---

## 阶段二：polystore-core

- [ ] `StorageClient` 接口
- [ ] `StorageContainer` 接口
- [ ] `StorageProvider` 接口（SPI）
- [ ] `StorageManager` 接口
- [ ] `SaveArgs` / `UrlArgs` 辅助类型
- [ ] `ContainerConfiguration` / `ContainerInfo`
- [ ] `TenantIdSupplier` 接口
- [ ] `TenantIsolationMode` 枚举
- [ ] 异常体系（`PolystoreException` 及子类）
- [ ] 单元测试（核心逻辑覆盖）

---

## 阶段三：polystore-spring

- [ ] `DefaultStorageManager` 实现（Provider 注册、容器初始化）
- [ ] 租户路径前缀拦截逻辑（`TenantIsolationMode.PATH_PREFIX`）
- [ ] `StorageProvider` 自动发现（扫描 Spring Bean）
- [ ] `FileSavedEvent` / `FileDeletedEvent` 事件发布
- [ ] 单元测试

---

## 阶段四：polystore-autoconfigure

- [ ] `PolystoreProperties` 配置属性类（绑定 yml）
- [ ] `PolystoreAutoConfiguration` 自动装配类
- [ ] `spring.factories` / `AutoConfiguration.imports` 注册
- [ ] 配置元数据（`additional-spring-configuration-metadata.json`，支持 IDE 提示）
- [ ] 集成测试（Spring Boot 上下文启动验证）

---

## 阶段五：存储后端实现

### 5.1 polystore-local
- [ ] `LocalStorageProvider` 实现
- [ ] 子目录自动创建
- [ ] 单元测试

### 5.2 polystore-minio
- [ ] `MinioStorageProvider` 实现
- [ ] Bucket 不存在时自动创建（可配置）
- [ ] 预签名 URL 生成
- [ ] 单元测试（需本地 MinIO 或 Testcontainers）

### 5.3 polystore-s3
- [ ] `S3StorageProvider` 实现（AWS SDK v2）
- [ ] S3-compatible endpoint 支持（pathStyleAccess）
- [ ] 预签名 URL 生成
- [ ] 单元测试（Testcontainers LocalStack）

### 5.4 polystore-aliyun-oss
- [ ] `AliyunOssStorageProvider` 实现
- [ ] 预签名 URL 生成
- [ ] 单元测试

### 5.5 polystore-azure
- [ ] `AzureBlobStorageProvider` 实现
- [ ] SAS Token URL 生成
- [ ] 单元测试

### 5.6 polystore-huawei-obs
- [ ] `HuaweiObsStorageProvider` 实现
- [ ] 预签名 URL 生成
- [ ] 单元测试

### 5.7 polystore-fastdfs
- [ ] `FastDfsStorageProvider` 实现
- [ ] `getUrl` 返回 Nginx 代理路径
- [ ] 单元测试

### 5.8 polystore-sftp
- [ ] `SftpStorageProvider` 实现
- [ ] JSch 连接池
- [ ] 单元测试

---

## 阶段六：质量与发布

- [ ] 各模块 Javadoc 补全
- [ ] README（项目简介、快速开始、各后端配置说明）
- [ ] CHANGELOG 初始化
- [ ] Maven 发布配置（`distributionManagement` / GPG 签名）
- [ ] CI 流水线（编译 + 测试）

---

## 备忘：暂不实现（后续规划）

- 分片上传（Multipart Upload）
- 文件 ID 生成器（基于时间戳 / 模板的路径生成策略）
- 镜像同步（写时同步到多个容器）
- CDN 签名 URL（CloudFront、阿里云 CDN）
