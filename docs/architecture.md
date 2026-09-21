# Polystore — 架构与功能设计

> 参考：C# `SharpAbp.Abp.FileStoring` + `Kayisoft.Abp.FileStoring.*`，面向 Java / Spring Boot 生态重新设计。

---

## 1. 设计目标

- **统一抽象**：向上提供与存储后端无关的 `StorageClient` API，业务代码无需感知底层存储类型。
- **多容器并存**：同一进程内可同时配置多个存储容器（container），每个容器独立指定后端类型与连接参数。
- **可插拔后端**：存储后端以 SPI 形式注册，按需引入对应子模块即可激活，不引入则不加载。
- **Spring Boot 友好**：提供 `autoconfigure` 子模块，基于 `application.yml` 零代码接入。
- **轻量无侵入**：核心抽象不依赖 Spring，可在非 Spring 环境中手动使用。

---

## 2. 整体架构

```
┌─────────────────────────────────────────────────────────┐
│                      Business Code                      │
└──────────────────────────┬──────────────────────────────┘
                           │ 注入
                           ▼
┌─────────────────────────────────────────────────────────┐
│               StorageManager（入口门面）                  │
│   getContainer(name) → StorageContainer                  │
└──────────────────────────┬──────────────────────────────┘
                           │
          ┌────────────────┼────────────────┐
          ▼                ▼                ▼
   StorageContainer  StorageContainer  StorageContainer
    (local/images)   (minio/dicom)    (s3/backup)
          │                │                │
          ▼                ▼                ▼
   LocalProvider     MinioProvider      S3Provider
```

### 核心流程

1. 启动时，`StorageManager` 根据配置初始化所有容器，每个容器持有一个 `StorageProvider` 实例。
2. 业务代码通过 `storageManager.getContainer("容器名")` 获取 `StorageContainer`。
3. 调用 `StorageContainer` 上的 save / get / delete / exists / getUrl 等操作。
4. `StorageContainer` 将操作委托给对应的 `StorageProvider` 执行。

---

## 3. 模块划分

```
polystore/
├── polystore-core              # 核心接口与抽象，无 Spring 依赖
├── polystore-spring            # Spring 集成（StorageManager Bean、事件、条件装配）
├── polystore-autoconfigure     # Spring Boot AutoConfiguration，读取 yml 配置
│
├── polystore-local             # 本地文件系统后端
├── polystore-minio             # MinIO 后端（MinIO Java SDK）
├── polystore-s3                # AWS S3 / S3-compatible 后端（AWS SDK v2）
├── polystore-azure             # Azure Blob Storage 后端
├── polystore-aliyun-oss        # 阿里云 OSS 后端
├── polystore-huawei-obs        # 华为云 OBS 后端
├── polystore-fastdfs           # FastDFS 后端
└── polystore-sftp              # SFTP 后端（Apache Commons VFS / JSch）
```

### 依赖层次

```
autoconfigure → spring → core
     ↓              ↓
  local/minio/...  (各后端仅依赖 core)
```

---

## 4. 核心接口（polystore-core）

### 4.1 StorageClient — 统一操作接口

```java
public interface StorageClient {

    /** 保存文件；inputStream 由调用方关闭 */
    void save(String fileName, InputStream inputStream, SaveArgs args);

    /** 获取文件流；调用方负责关闭返回的流 */
    InputStream get(String fileName);

    /** 删除文件；文件不存在时静默返回 */
    void delete(String fileName);

    /** 检查文件是否存在 */
    boolean exists(String fileName);

    /** 获取可访问 URL（对象存储返回预签名 URL；本地存储返回访问路径） */
    String getUrl(String fileName, UrlArgs args);

    /** 批量删除 */
    void deleteAll(Collection<String> fileNames);
}
```

### 4.2 StorageContainer — 容器（持有配置 + Client）

```java
public interface StorageContainer extends StorageClient {

    /** 容器名称，全局唯一 */
    String getName();

    /** 底层 Provider 类型标识，如 "local"、"minio"、"s3" */
    String getProviderType();

    /** 容器元数据（Provider 原始配置的只读视图） */
    ContainerInfo getInfo();
}
```

### 4.3 StorageProvider — 后端实现 SPI

```java
public interface StorageProvider {

    /** Provider 类型标识，与配置中 type 字段对应，如 "minio" */
    String getType();

    /** 根据容器配置创建一个 StorageContainer 实例 */
    StorageContainer createContainer(ContainerConfiguration config);
}
```

### 4.4 StorageManager — 全局管理器

```java
public interface StorageManager {

    /** 获取指定名称的容器；容器不存在时抛出 ContainerNotFoundException */
    StorageContainer getContainer(String name);

    /** 获取默认容器（配置中 default: true 的那个） */
    StorageContainer getDefaultContainer();

    /** 返回所有已注册容器的名称 */
    Collection<String> containerNames();
}
```

### 4.5 辅助类型

```java
// 保存参数
public class SaveArgs {
    private String contentType;       // MIME 类型
    private Map<String, String> metadata; // 自定义元数据
    private boolean overwrite = true; // 同名文件是否覆盖
}

// URL 参数
public class UrlArgs {
    private Duration expiry = Duration.ofHours(1); // 预签名过期时间
    private boolean inline = false;                // Content-Disposition inline
}

// 容器配置（对应 yml 中一个容器块）
public class ContainerConfiguration {
    private String name;
    private String type;          // provider 类型
    private boolean isDefault;
    private Map<String, Object> properties; // provider 专属参数
}

// 容器信息（只读）
public class ContainerInfo {
    private String name;
    private String providerType;
    private boolean isDefault;
}
```

---

## 5. 各存储后端设计

### 5.1 Local（本地文件系统）

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `basePath` | 存储根目录（绝对路径） | 必填 |
| `urlPrefix` | 文件 URL 前缀（HTTP 静态资源地址） | `""` |
| `createDirectories` | 子目录不存在时自动创建 | `true` |

- `getUrl` 返回 `urlPrefix + "/" + fileName`，不产生预签名。
- 适合开发环境、内网无对象存储的场景。

### 5.2 MinIO

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `endpoint` | MinIO 服务地址 | 必填 |
| `accessKey` | Access Key | 必填 |
| `secretKey` | Secret Key | 必填 |
| `bucketName` | 桶名 | 必填 |
| `region` | 区域 | `""` |
| `secure` | 是否使用 HTTPS | `false` |
| `urlExpiry` | 预签名 URL 过期时间（秒） | `3600` |

- 底层使用 `io.minio:minio` SDK。
- 桶不存在时可选自动创建（`createBucketIfAbsent: true`）。

### 5.3 AWS S3 / S3-compatible

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `endpoint` | 服务端点（留空则使用 AWS 官方） | `""` |
| `region` | 区域 | 必填 |
| `accessKeyId` | Access Key ID | 必填 |
| `secretAccessKey` | Secret Access Key | 必填 |
| `bucketName` | 桶名 | 必填 |
| `pathStyleAccess` | 强制路径访问模式（兼容 Ceph 等） | `false` |
| `urlExpiry` | 预签名 URL 过期时间（秒） | `3600` |

- 底层使用 AWS SDK for Java v2（`software.amazon.awssdk`）。
- 通过 `endpoint` 覆盖可对接 KS3、Ceph、Scaleway 等 S3-compatible 存储。

### 5.4 Azure Blob Storage

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `connectionString` | 存储账户连接字符串 | 必填（与 accountName/Key 二选一） |
| `accountName` | 存储账户名 | — |
| `accountKey` | 存储账户密钥 | — |
| `containerName` | 容器名 | 必填 |
| `sasExpiry` | SAS Token 过期时间（秒） | `3600` |

- 底层使用 `com.azure:azure-storage-blob`。

### 5.5 阿里云 OSS

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `endpoint` | OSS Endpoint | 必填 |
| `accessKeyId` | Access Key ID | 必填 |
| `accessKeySecret` | Access Key Secret | 必填 |
| `bucketName` | Bucket 名 | 必填 |
| `urlExpiry` | 预签名 URL 过期时间（秒） | `3600` |
| `useInternal` | 内网 Endpoint | `false` |

- 底层使用 `com.aliyun.oss:aliyun-sdk-oss`。

### 5.6 华为云 OBS

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `endpoint` | OBS Endpoint | 必填 |
| `accessKey` | Access Key | 必填 |
| `secretKey` | Secret Key | 必填 |
| `bucketName` | Bucket 名 | 必填 |
| `urlExpiry` | 预签名 URL 过期时间（秒） | `3600` |

- 底层使用 `com.huaweicloud:esdk-obs-java`。

### 5.7 FastDFS

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `trackerServers` | Tracker 服务器地址列表（host:port） | 必填 |
| `connectTimeout` | 连接超时（毫秒） | `5000` |
| `networkTimeout` | 读超时（毫秒） | `30000` |
| `charset` | 字符集 | `UTF-8` |
| `urlPrefix` | 文件 URL 前缀（Nginx 代理地址） | `""` |

- 底层使用 `com.github.tobato:fastdfs-client`。
- `getUrl` 返回 `urlPrefix + "/" + fileId`，无预签名。

### 5.8 SFTP

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `host` | SFTP 主机 | 必填 |
| `port` | 端口 | `22` |
| `username` | 用户名 | 必填 |
| `password` | 密码 | `""` |
| `privateKeyPath` | 私钥文件路径（与 password 二选一） | `""` |
| `basePath` | 远程根目录 | 必填 |
| `urlPrefix` | 文件 URL 前缀 | `""` |
| `poolSize` | 连接池大小 | `5` |

- 底层使用 `com.github.mwiede:jsch`（JSch 维护分支）。
- 连接复用连接池，避免每次操作重新建立 SSH 会话。

---

## 6. 多租户支持

### 6.1 设计范围与边界

C# 端的多租户依赖 ABP 完整的多租户模块（`ICurrentTenant`、租户切换、租户生命周期管理等）。polystore **不引入任何租户管理框架**，只解决一个具体问题：

> 同一个存储容器（同一个 Bucket）内，不同租户的文件通过路径前缀物理隔离。

C# 端 `AppendTenantToPath=true` 对应的正是这个能力，polystore 将其作为容器级的轻量配置实现。

### 6.2 租户 ID 的来源

polystore 对多租户的全部依赖只有一个极简接口：

```java
@FunctionalInterface
public interface TenantIdSupplier {
    /** 返回当前租户 ID；无多租户场景返回 null */
    String get();
}
```

**主路径（隐式）**：应用注册一次 `TenantIdSupplier`，之后所有调用点无需重复传递租户 ID，框架自动读取：

```java
// 注册一次，接入自己的租户上下文
storageManager = PolystoreBuilder.builder()
    .tenantIdSupplier(() -> MyTenantContext.currentTenantId())
    .build();

// 调用点干净，无样板代码
container.save("photo.jpg", stream, SaveArgs.defaults());
```

**覆盖路径（显式）**：`SaveArgs` 可显式传入 `tenantId`，优先级高于 `TenantIdSupplier`，适用于后台批处理、跨租户管理操作、单元测试等场景：

```java
container.save("photo.jpg", stream, SaveArgs.builder()
    .tenantId("tenant-abc")
    .build());
```

**租户 ID 解析优先级**：

```
SaveArgs.tenantId（显式）> TenantIdSupplier（隐式）> null
```

不注册 `TenantIdSupplier` 时默认返回 `null`。`PATH_PREFIX` 模式下最终租户 ID 为 null 时抛出 `TenantIdMissingException`。

### 6.3 容器级隔离开关

每个容器独立控制是否启用路径隔离，默认关闭：

```java
public enum TenantIsolationMode {
    NONE,        // 所有租户共享路径（默认）
    PATH_PREFIX  // 文件路径自动加 {tenantId}/ 前缀
}
```

### 6.4 路径转换规则

`tenantIsolation = PATH_PREFIX` 时，所有操作透明加前缀：

| 调用方传入 | 实际操作路径 |
|-----------|-------------|
| `images/photo.jpg` | `{tenantId}/images/photo.jpg` |
| `2024/01/abc.dcm` | `{tenantId}/2024/01/abc.dcm` |

租户 ID 按优先级解析：`SaveArgs.tenantId` > `TenantIdSupplier` > null。最终为 null 且容器开启了 `PATH_PREFIX` 时，抛出 `TenantIdMissingException`。

### 6.5 yml 配置示例

```yaml
polystore:
  containers:
    - name: dicom
      type: minio
      tenant-isolation: PATH_PREFIX   # 开启路径隔离
      minio:
        endpoint: http://minio.internal:9000
        access-key: admin
        secret-key: password
        bucket-name: dicom

    - name: public-assets
      type: local
      tenant-isolation: NONE          # 公共资源，不隔离（默认可省略）
      local:
        base-path: /data/public
        url-prefix: https://cdn.example.com
```

---

## 7. 配置方案（Spring Boot）

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
      minio:
        endpoint: http://minio.internal:9000
        access-key: admin
        secret-key: password
        bucket-name: dicom
        create-bucket-if-absent: true

    - name: backup
      type: s3
      s3:
        region: cn-northwest-1
        access-key-id: AKIAXXXXXXXX
        secret-access-key: xxxxxxxx
        bucket-name: my-backup

    - name: archive
      type: aliyun-oss
      aliyun-oss:
        endpoint: oss-cn-hangzhou.aliyuncs.com
        access-key-id: LTAIxxxxxxxx
        access-key-secret: xxxxxxxx
        bucket-name: my-archive
```

---

## 8. 异常体系

```
PolystoreException (基类)
├── ContainerNotFoundException        # 容器名不存在
├── StorageProviderNotFoundException  # Provider 类型未注册
├── FileNotFoundException             # 文件不存在
├── FileAlreadyExistsException        # 文件已存在且 overwrite=false
├── TenantIdMissingException          # PATH_PREFIX 模式下无法解析到租户 ID
└── StorageOperationException         # 后端 I/O 操作失败（包含 cause）
```

---

## 9. 扩展点

### 8.1 自定义 Provider

实现 `StorageProvider` 接口，将实现类注册为 Spring Bean，框架会自动发现并注册：

```java
@Component
public class MyCustomProvider implements StorageProvider {
    @Override public String getType() { return "my-custom"; }
    @Override public StorageContainer createContainer(ContainerConfiguration config) { ... }
}
```

### 8.2 文件名拦截器（FileNameResolver）

在 save / get 前对文件名做统一转换（如路径规范化、加前缀）：

```java
public interface FileNameResolver {
    String resolve(String rawFileName, String containerName);
}
```

### 8.3 操作事件

保存、删除完成后发布 Spring `ApplicationEvent`，便于审计日志、缓存失效等：

- `FileSavedEvent`
- `FileDeletedEvent`

---

## 10. 构建工具规范

**本仓库使用 Maven Wrapper，禁止依赖系统全局 Maven。**

所有构建命令统一通过仓库根目录的 `mvnw`（Linux/macOS）或 `mvnw.cmd`（Windows）执行：

```bash
# 构建
./mvnw clean install

# 跳过测试
./mvnw clean install -DskipTests

# 运行单模块测试
./mvnw test -pl polystore-core
```

Maven Wrapper 所需文件：

```
polystore/
├── .mvn/
│   └── wrapper/
│       └── maven-wrapper.properties   # 指定 Maven 版本与下载地址
├── mvnw                               # Unix 启动脚本
└── mvnw.cmd                           # Windows 启动脚本
```

`maven-wrapper.properties` 中固定 Maven 版本，确保所有开发者与 CI 使用同一版本，不受本地环境影响。

---

## 11. 模块依赖说明（Maven）

| 场景 | 引入依赖 |
|------|---------|
| 只要核心接口（不依赖 Spring） | `polystore-core` |
| Spring 应用，手动配置 | `polystore-spring` + 对应后端子模块 |
| Spring Boot 应用，yml 配置 | `polystore-autoconfigure` + 对应后端子模块 |

---

## 12. 后续规划（超出 v1 范围）

- **分片上传（Multipart Upload）**：大文件分块上传，对接 S3 / MinIO 分片 API。
- **文件 ID 生成器**：参考 `Kayisoft.Abp.FileStoring.FileIds`，提供基于时间戳 / 模板的路径生成策略。
- **镜像同步**：写时同步到多个容器（主备模式）。
- **CDN 签名 URL**：针对 CloudFront、阿里云 CDN 等生成带签名的 CDN 地址。
