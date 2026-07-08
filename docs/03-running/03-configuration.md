[English Version](03-configuration.en.md)

# 03 配置说明

> 最后更新：2026-07-08  
> 对应源码版本：当前工作区

## 主配置文件

`src/main/resources/application.properties`

---

## 核心配置项

### HTTP 服务

```properties
quarkus.http.port=12360
quarkus.http.host=0.0.0.0
```

- 服务监听端口和绑定地址。
- 可通过环境变量 `QUARKUS_HTTP_PORT`、`QUARKUS_HTTP_HOST` 或系统属性覆盖。

---

### 数据库

```properties
quarkus.datasource.db-kind=h2
quarkus.datasource.jdbc.url=jdbc:h2:file:${user.home}/.damning-proxy/data;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
quarkus.datasource.username=sa
quarkus.datasource.password=sa
quarkus.hibernate-orm.database.generation=update
quarkus.hibernate-orm.log.sql=false
```

| 配置项 | 说明 |
|---|---|
| `db-kind` | 数据库类型，默认 H2 |
| `jdbc.url` | H2 文件数据库路径，`${user.home}` 为用户主目录 |
| `username` / `password` | H2 默认账号密码 |
| `database.generation=update` | 自动更新表结构 |
| `log.sql=false` | 关闭 SQL 日志 |

切换 PostgreSQL 示例：

```properties
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/damning_proxy
quarkus.datasource.username=damning
quarkus.datasource.password=secret
quarkus.hibernate-orm.database.generation=update
```

切换 MySQL 类似，需添加对应 JDBC 依赖。

---

### CORS

```properties
quarkus.http.cors=true
quarkus.http.cors.origins=*
quarkus.http.cors.methods=GET,POST,PUT,DELETE,OPTIONS
quarkus.http.cors.headers=accept,authorization,content-type,x-requested-with
```

- 默认开启跨域，允许任意来源。
- 生产环境应限制 `origins`。

---

### 日志

```properties
quarkus.log.level=INFO
quarkus.log.category."com.xenoamess.damning_proxy".level=DEBUG
```

- 全局日志级别 `INFO`。
- 项目包内日志级别 `DEBUG`。

---

### 代理自定义配置

```properties
damning-proxy.default-timeout-ms=30000
```

- 默认上行连接超时毫秒。
- 单个 Profile 的 `timeoutMs` 可覆盖此值。

`src/main/resources/application.properties:27`

---

### 日志截断配置

控制 TrafficLog 中存储的各字段最大长度（字符数），超出部分会被截断并追加 `...[truncated]`。

```properties
damning-proxy.log.max-body-length=1073741824
damning-proxy.log.max-headers-length=2000
damning-proxy.log.max-plugin-logs-length=5000
damning-proxy.log.max-friendly-snapshots-length=8000
```

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `damning-proxy.log.max-body-length` | `1073741824` | requestBody / responseBody 最大存储长度 |
| `damning-proxy.log.max-headers-length` | `2000` | requestHeaders / responseHeaders 最大存储长度 |
| `damning-proxy.log.max-plugin-logs-length` | `5000` | pluginLogs 最大存储长度 |
| `damning-proxy.log.max-friendly-snapshots-length` | `8000` | friendlyPluginSnapshots 最大存储长度 |

### 日志保留配置

```properties
damning-proxy.log.max-count=100000
```

- 最大日志条目数，超出后自动删除最旧的日志。
- 每写入 100 条日志检查一次。

### 频率限制

```properties
damning-proxy.rate-limit.max-requests=60
damning-proxy.rate-limit.window-seconds=60
```

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `damning-proxy.rate-limit.max-requests` | `60` | 每个窗口内每个实例 slug 允许的最大请求数 |
| `damning-proxy.rate-limit.window-seconds` | `60` | 滑动窗口时长（秒） |

`src/main/resources/application.properties:27`

---

## 环境变量覆盖

Quarkus 支持通过环境变量覆盖配置，规则：把点号 `.` 替换为下划线 `_`，并转大写。

示例：

```bash
export QUARKUS_HTTP_PORT=8080
export QUARKUS_DATASOURCE_JDBC_URL=jdbc:h2:file:/data/damning-proxy/data
export DAMNING_PROXY_DEFAULT_TIMEOUT_MS=60000
```

---

## 测试配置

测试使用独立配置：`src/test/resources/application.properties`

```properties
quarkus.datasource.db-kind=h2
quarkus.datasource.jdbc.url=jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
quarkus.hibernate-orm.database.generation=drop-and-create
```

- 使用内存 H2，避免文件锁问题。
- 每次测试都会 drop 并重新创建表。

---

## 配置项速查表

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `quarkus.http.port` | `12360` | 服务端口 |
| `quarkus.http.host` | `0.0.0.0` | 绑定地址 |
| `quarkus.datasource.db-kind` | `h2` | 数据库类型 |
| `quarkus.datasource.jdbc.url` | `${user.home}/.damning-proxy/data` | JDBC URL |
| `quarkus.hibernate-orm.database.generation` | `update` | 自动建表/更新 |
| `quarkus.http.cors` | `true` | 是否开启 CORS |
| `quarkus.http.cors.origins` | `*` | 允许的跨域来源 |
| `quarkus.log.level` | `INFO` | 全局日志级别 |
| `damning-proxy.default-timeout-ms` | `30000` | 默认上游超时 |
| `damning-proxy.log.max-body-length` | `1073741824` | request/response body 最大存储长度 |
| `damning-proxy.log.max-headers-length` | `2000` | request/response headers 最大存储长度 |
| `damning-proxy.log.max-plugin-logs-length` | `5000` | pluginLogs 最大存储长度 |
| `damning-proxy.log.max-friendly-snapshots-length` | `8000` | friendlyPluginSnapshots 最大存储长度 |
| `damning-proxy.log.max-count` | `100000` | 最大日志条目数，超出自动删旧 |
| `damning-proxy.rate-limit.max-requests` | `60` | 每窗口每实例最大请求数 |
| `damning-proxy.rate-limit.window-seconds` | `60` | 频率限制滑动窗口秒数 |
