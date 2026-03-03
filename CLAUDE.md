# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

这是一个基于 Spring Boot 3.5.8 的文件分享应用（FlashShare），支持文本和文件的临时分享功能。使用 Undertow 作为 Web 服务器，采用持久化存储方案，支持高并发文件传输。

## 技术栈

- Java 17
- Spring Boot 3.5.8 (使用 Undertow 替代 Tomcat)
- Caffeine 缓存
- Jackson (JSON 序列化)
- Lombok
- CommonMark (Markdown 渲染)
- 前端：原生 HTML/CSS/JavaScript

## 常用命令

### 构建和运行

```bash
# 编译项目
mvn clean compile

# 打包（生成 JAR）
mvn clean package

# 运行应用
java -jar target/flashshare-1.0.jar

# 或使用启动脚本（后台运行）
./start.sh

# 开发模式运行
mvn spring-boot:run
```

### 测试

```bash
# 运行所有测试
mvn test

# 运行单个测试类
mvn test -Dtest=ClassName

# 运行单个测试方法
mvn test -Dtest=ClassName#methodName
```

### GraalVM Native Image（可选）

```bash
# 构建原生镜像
mvn -Pnative native:compile
```

## 核心架构

### 存储架构（双层存储）

项目采用**内存缓存 + 持久化文件**的双层存储架构：

1. **PersistentTextStorage**: 核心存储服务
   - 使用 Caffeine 作为一级缓存（24小时过期，最多5000条）
   - 使用 JSON 文件作为持久化层（`./data/shares_metadata.json`）
   - 异步写入策略：每30秒批量刷盘，应用关闭时立即保存
   - 读写锁保证并发安全

2. **FileStorageService**: 文件存储服务
   - 物理文件存储在 `${storage.path}` 目录（默认 `files/`）
   - 使用 UUID 生成唯一文件名
   - 支持自动清理24小时前的过期文件

### 数据模型

**ShareContent** (`cn.hellocsc.model.ShareContent`)：
- `shareId`: 4位数字分享码
- `textContent`: 文本内容（文本分享）
- `fileName`, `filePath`, `size`, `contentType`: 文件元数据（文件分享）
- `isFile`: 区分文本/文件分享
- `isRichText`: 是否富文本（Markdown）
- `createTime`: 创建时间（24小时后过期）
- `viewCount`: 访问计数

### 核心服务层

- **ShareService**: 业务逻辑层
  - `createTextShare()`: 创建文本分享
  - `createFileShare()`: 创建文件分享
  - `getShareContent()`: 获取分享内容（自动增加访问计数）
  - `cleanupExpiredShares()`: 清理过期数据

- **ShareController**: REST API 控制器
  - `POST /api/share/text`: 创建文本分享
  - `POST /api/share/file`: 上传文件分享
  - `GET /api/share?shareId=xxx`: 获取分享内容
  - `GET /api/share/download?shareId=xxx`: 下载文件（零拷贝优化）

### 性能优化特性

1. **零拷贝文件下载**: 使用 `FileChannel.transferTo()` 直接传输文件到 Socket，避免用户态内存拷贝
2. **虚拟线程**: 启用 JDK 21 虚拟线程（`spring.threads.virtual.enabled=true`）
3. **Undertow 优化**:
   - 直接缓冲区（`direct-buffers: true`）
   - 64KB 缓冲区大小
   - IO 线程数 = CPU 核心数
   - 工作线程数 = 256

### 定时任务

**CleanupTask** (`cn.hellocsc.task.CleanupTask`):
- 每5分钟执行一次清理任务
- 删除24小时前的物理文件
- 触发 Caffeine 缓存清理

## 配置说明

### application.yml 关键配置

```yaml
server:
  port: 8088
  undertow:
    direct-buffers: true
    buffer-size: 65536
    threads:
      io: 4
      worker: 256

spring:
  threads:
    virtual:
      enabled: true
  servlet:
    multipart:
      max-file-size: 5000MB
      max-request-size: 5000MB
      file-size-threshold: 2MB

app:
  storage:
    metadata-file: ./data/shares_metadata.json
    migrate: false          # 数据迁移开关
    performance-test: false # 性能测试开关
    repair: true            # 数据修复开关

storage:
  path: ${STORAGE_PATH:files}
  max-size: 524288000
  cleanup-interval: 300000
```

## 前端结构

- `src/main/resources/static/index.html`: 主页（上传界面）
- `src/main/resources/static/view.html`: 查看分享内容
- `src/main/resources/static/share.html`: 分享成功页面
- `src/main/resources/static/js/`: JavaScript 模块
  - `upload.js`: 文件上传逻辑
  - `view.js`: 内容查看逻辑
  - `utils.js`: 工具函数

## 数据持久化

### 元数据文件格式

`./data/shares_metadata.json` 存储所有分享记录的元数据（JSON 格式）：

```json
{
  "1234": {
    "shareId": "1234",
    "textContent": "...",
    "isFile": false,
    "createTime": "2024-12-12T10:30:00",
    "viewCount": 5
  }
}
```

### 数据迁移工具

如果需要从旧版本（纯内存存储）迁移数据：
1. 设置 `app.storage.migrate=true`
2. 启动应用（自动执行迁移）
3. 迁移完成后改回 `false`

## 开发注意事项

1. **文件路径处理**: `ShareContent.filePath` 存储相对路径（文件名），`FileStorageService.getFile()` 负责解析为绝对路径
2. **并发安全**: 所有存储操作都通过 `PersistentTextStorage` 的读写锁保护
3. **过期策略**: 分享内容24小时后自动过期，物理文件也会在24小时后被清理
4. **ID 生成**: 使用4位数字作为分享码，冲突时最多重试5次
5. **异常处理**: `GlobalExceptionHandler` 统一处理 `ShareNotFoundException` 等业务异常

## 日志和监控

- 使用 Lombok 的 `@Slf4j` 注解
- 关键操作都有日志记录（创建分享、下载文件、清理任务等）
- 客户端断开连接会被识别并记录为 INFO 级别（非错误）

## 部署

生产环境部署：
1. 确保 Java 17+ 运行环境
2. 配置 `STORAGE_PATH` 环境变量（可选）
3. 确保 `./data/` 和 `./files/` 目录有读写权限
4. 使用 `start.sh` 或 systemd 管理进程
5. 建议定期备份 `./data/shares_metadata.json`
