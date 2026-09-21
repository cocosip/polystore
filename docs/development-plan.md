# Polystore — 开发计划

> 勾选表示该项已完成。模块内各子任务全部完成后，再勾选模块标题。

---

## 阶段一：项目骨架 ✅

- [x] 初始化 Maven 多模块父 POM
- [x] 配置 Maven Wrapper（固定版本，统一 3.9.16）
- [x] 各子模块目录与 `pom.xml` 占位创建
- [x] 配置统一的编译版本（Java 21）、编码、插件版本管理

> 2026-09-21：模块结构与版本体系与 stow / latchq 对齐——模块采用 `{name}-core` + `{name}-spring-boot-starter` 模式；依赖与插件版本采用 stow 的一套（Spring Boot 3.5.6、JUnit 5.13.4、compiler 3.14.1、jacoco 0.8.13、spotless 2.46.1 palantir 格式 + sortPom、spotbugs 4.9.8.1、flatten 1.7.3、enforcer 3.6.2 等），并补充发布元数据（scm / license / central 发布 profile）。

---

## 阶段二：polystore-core ✅

- [x] `StorageClient` 接口
- [x] `StorageContainer` 接口
- [x] `StorageProvider` 接口（SPI）
- [x] `StorageManager` 接口
- [x] `SaveArgs` / `UrlArgs` 辅助类型
- [x] `ContainerConfiguration` / `ContainerInfo`
- [x] `TenantIdSupplier` 接口
- [x] `TenantIsolationMode` 枚举
- [x] 异常体系（`PolystoreException` 及子类）
- [x] 单元测试（核心逻辑覆盖）

> 2026-09-21：24 个单元测试全绿。实现说明：包名 `io.github.cocosip.polystore`，异常位于 `exception` 子包；`SaveArgs` / `ContainerConfiguration` 均为不可变对象（builder + 防御性拷贝 + 只读 map）；`SaveArgs.save(fileName, stream)` 与 `getUrl(fileName)` 提供默认参数重载。文件不存在/已存在异常因与 `java.io` / `java.nio.file` 同名类冲突，命名为 `StorageFileNotFoundException` / `StorageFileAlreadyExistsException`。

---

## 阶段三：polystore-spring-boot-starter ✅

- [x] `PolystoreProperties` 配置属性类（绑定 yml）
- [x] `DefaultStorageManager` 实现（Provider 注册、容器初始化）
- [x] 租户路径前缀拦截逻辑（`TenantIsolationMode.PATH_PREFIX`）
- [x] `StorageProvider` 自动发现（扫描 Spring Bean）
- [x] `PolystoreAutoConfiguration` 自动装配类 + `AutoConfiguration.imports` 注册
- [x] `FileSavedEvent` / `FileDeletedEvent` 事件发布
- [x] 配置元数据（`additional-spring-configuration-metadata.json`，支持 IDE 提示）
- [x] 单元测试（含 Spring Boot 上下文启动验证）

> 2026-09-21：59 个单元测试全绿（core 28 + starter 31）。实现说明：core 新增 `DefaultStorageContainer`（后端模块只依赖 core，具体容器实现必须下沉到 core 供 Provider 复用）；Provider 双通道发现（Spring Bean 优先 + 后端模块经 ServiceLoader 声明，二者都不依赖 Spring）；yml 中 provider 专属参数按 type 键分组（如 `minio:` 块），`ContainerConfigurationFactory` 经 Binder API 读取并合并进 `ContainerConfiguration.properties`（键保持原样，由后端自行归一化）；默认容器绑定键为 `default`；装饰器顺序 raw → tenant 前缀 → 事件（事件携带调用方逻辑文件名，不含租户前缀）；非 Spring 场景用 `PolystoreBuilder` 手动装配。

---

## 阶段四：存储后端实现

### 4.1 polystore-local
- [ ] `LocalStorageProvider` 实现
- [ ] 子目录自动创建
- [ ] 单元测试

### 4.2 polystore-minio
- [ ] `MinioStorageProvider` 实现
- [ ] Bucket 不存在时自动创建（可配置）
- [ ] 预签名 URL 生成
- [ ] 单元测试（需本地 MinIO 或 Testcontainers）

### 4.3 polystore-s3
- [ ] `S3StorageProvider` 实现（AWS SDK v2）
- [ ] S3-compatible endpoint 支持（pathStyleAccess）
- [ ] 预签名 URL 生成
- [ ] 单元测试（Testcontainers LocalStack）

### 4.4 polystore-aliyun-oss
- [ ] `AliyunOssStorageProvider` 实现
- [ ] 预签名 URL 生成
- [ ] 单元测试

### 4.5 polystore-azure
- [ ] `AzureBlobStorageProvider` 实现
- [ ] SAS Token URL 生成
- [ ] 单元测试

### 4.6 polystore-huawei-obs
- [ ] `HuaweiObsStorageProvider` 实现
- [ ] 预签名 URL 生成
- [ ] 单元测试

### 4.7 polystore-fastdfs
- [ ] `FastDfsStorageProvider` 实现
- [ ] `getUrl` 返回 Nginx 代理路径
- [ ] 单元测试

### 4.8 polystore-sftp
- [ ] `SftpStorageProvider` 实现
- [ ] JSch 连接池
- [ ] 单元测试

---

## 阶段五：质量与发布

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
