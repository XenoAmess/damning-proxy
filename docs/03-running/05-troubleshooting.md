[English Version](05-troubleshooting.en.md)

# 05 常见问题排查

> 最后更新：2026-06-18  
> 对应源码版本：当前工作区

## 启动问题

### 端口被占用

```text
BindException: Address already in use
```

解决：

```bash
mvn quarkus:dev -Dquarkus.http.port=8080
```

---

### JDK 版本错误 / Maven 使用默认 JDK 25

现象：

```text
Java 21 is required. Tests and runtime use libraries that are not compatible with newer JDKs.
```

或执行 `mvn -version` 显示 `Java version: 25.x`，即使 `JAVA_HOME` 已指向 JDK 21。

原因：系统默认 `java` 可能是 GraalVM 25，`mvn` 脚本根据 `JAVA_HOME` 选择 JDK，但如果只设置 `JAVA_HOME` 而 `PATH` 仍指向 JDK 25 的 `java`，子进程可能不一致。

解决：

```bash
export JAVA_HOME=/home/xenoamess/.jdks/jdk-21.0.7+6
export PATH=$JAVA_HOME/bin:$PATH
java -version   # 应显示 21
mvn -version    # 应显示 Java 21
```

开发模式启动命令：

```bash
setsid bash -c 'mvn quarkus:dev -DskipTests > /tmp/daming-proxy-dev.log 2>&1' </dev/null &
```

---

### 启动后终端被阻塞 / opencode 主线程挂起

现象：执行 `mvn quarkus:dev` 后 opencode 不再响应，或后台启动后进程很快退出。

原因：Quarkus dev mode 默认读取标准输入等待命令（如 `r` 恢复测试、`e` 编辑参数），并且会暂停测试。普通 `nohup ... &` 可能因 stdin 被关闭而异常；`setsid bash -c 'mvn ...'` 会让 opencode 等待内层 shell 直到 mvn 退出。

解决：使用 `setsid` + 内层后台 + `</dev/null`：

```bash
export JAVA_HOME=/home/xenoamess/.jdks/jdk-21.0.7+6
export PATH=$JAVA_HOME/bin:$PATH
setsid bash -c 'mvn quarkus:dev -DskipTests > /tmp/daming-proxy-dev.log 2>&1 &' </dev/null &
```

验证：

```bash
pgrep -f quarkus:dev
tail -f /tmp/daming-proxy-dev.log
```

---

### H2 数据库锁定

开发模式下如果 H2 文件数据库被多个进程占用，可能出现锁定错误。

解决：

- 停止其他进程。
- 删除 `${user.home}/.damning-proxy/data.lock.db`。
- 或使用内存 H2 临时测试。

---

## 构建问题

### 前端构建失败

常见原因：

- Node/pnpm 下载失败（网络问题）。
- `admin-web/pnpm-lock.yaml` 与 `package.json` 不一致。

解决：

```bash
cd admin-web
rm -rf node_modules pnpm-lock.yaml
corepack enable pnpm
pnpm install
pnpm run build
```

---

## 代理问题

### 404 Instance not found

- 检查 `instanceSlug` 是否正确。
- 检查 Instance 是否启用。
- 检查是否创建了 Instance（StartupMigration 会在无 Instance 时自动从 Profile 创建，但若手动删除可能不存在）。

### 403 Instance disabled / Upstream profile disabled

- 检查 Instance 和 Profile 的 `enabled` 字段。

### 502 Upstream request failed

- 检查 Profile 的 `baseUrl` 是否正确。
- 检查上游服务是否可达。
- 检查 `bearerToken` 是否有效。
- 查看日志中的上游请求地址和错误信息。

### 流式请求返回 event: error

- 上游连接失败、返回非 2xx 状态码或流式读取异常时，代理会发送 SSE `event: error` 事件并结束流。
- 检查事件体中的 `message` 和 `code` 字段定位原因。
- 查看 `/v1/health` 中的 `circuitBreakers` 快照确认是否处于熔断状态。

---

## 插件问题

### 插件修改后未生效

- 插件保存时会重新加载，但已运行的实例可能缓存了旧脚本；保存后建议重新触发请求验证。
- 使用插件调试器页面的「试运行」功能可快速验证脚本。

### 插件报错但流水线继续

- `PluginExecutionService` 会捕获插件异常并记录，不中断后续插件。
- 查看 `/api/logs/{id}/friendly` 中的插件错误信息。

### Groovy/JS 脚本语法问题

- 先在 `PluginEngineTest` 风格的小用例中测试脚本片段。
- 注意 Groovy 对 Map/List 的操作方式与 JS 不同。

---

## 日志问题

### 日志没有记录

- 检查请求是否到达代理端点。
- 检查数据库连接是否正常。
- 检查 `TrafficLogService` 是否因异常未提交事务。

### 日志内容被截断

- `TrafficLogService` 对 body/header/pluginLogs/snapshots 有长度上限。
- 大响应会被截断，详见 [02-design/01-data-model.md](../02-design/01-data-model.md)。

---

## 调试技巧

1. 开启 DEBUG 日志：

```properties
quarkus.log.category."com.xenoamess.damning_proxy".level=DEBUG
```

2. 查看上游请求地址：日志中搜索 `Upstream request:`。

3. 查看完整异常：`GlobalExceptionMapper` 会打印所有未捕获异常。
