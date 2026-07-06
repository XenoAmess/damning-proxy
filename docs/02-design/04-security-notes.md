[English Version](04-security-notes.en.md)

# 04 安全提示

> 最后更新：2026-06-17  
> 对应源码版本：当前工作区

## 重要安全声明

damning-proxy 默认面向**本地/可信内网环境**设计。当前版本未内置管理后台认证，且插件引擎拥有近乎完整的 JVM 权限。部署到生产环境前，请务必评估以下风险并采取防护措施。

---

## 1. 管理后台无认证

- 所有 `/api/*` 端点均公开可访问。
- 任何人只要能访问服务，就能创建/修改/删除上游配置、插件、实例和日志。

### 建议

- 仅在本地或受控内网运行。
- 如需公网访问，前置反向代理并加认证（如 Nginx Basic Auth、OAuth2 代理）。
- 未来版本可考虑为 `/api/*` 增加 Token/Session 认证。

相关端点：[04-api/02-admin-api.md](../04-api/02-admin-api.md)

---

## 2. CORS 默认全开

`src/main/resources/application.properties:13`

```properties
quarkus.http.cors=true
quarkus.http.cors.origins=*
quarkus.http.cors.methods=GET,POST,PUT,DELETE,OPTIONS
```

这意味着任意网页都可以通过浏览器调用本服务。

### 建议

- 生产环境将 `quarkus.http.cors.origins` 限制为可信域名。
- 或关闭 CORS 并通过同源反向代理访问管理后台。

---

## 3. 插件可执行任意代码

### Groovy

`src/main/java/com/xenoamess/damning_proxy/plugin/engine/GroovyPluginEngine.java:15`

Groovy 脚本默认拥有完整 JVM 访问能力，可读写文件、执行系统命令、访问网络等。

### JavaScript

`src/main/java/com/xenoamess/damning_proxy/plugin/engine/JavaScriptPluginEngine.java:34`

```java
Context.newBuilder("js").allowAllAccess(true).build()
```

GraalJS 也开启了 `allowAllAccess(true)`，脚本可调用 Java 类、访问文件系统等。

### 建议

- 不要运行来源不明的插件脚本。
- 不要在插件中硬编码密钥、密码等敏感信息。
- 如需限制插件权限，未来应引入沙箱或禁用高危 API。

---

## 4. 上游 Token 与自定义 Header

- `ProxyProfile.bearerToken` 和 `customHeaders` 会原样发送到上游源。
- 这些敏感信息存储在 H2 数据库中，数据库文件默认位于 `${user.home}/.damning-proxy/data.mv.db`。

### 建议

- 妥善保管数据库文件访问权限。
- 考虑使用环境变量或密钥管理系统替代明文存储（未来改进方向）。

---

## 5. 日志中可能包含敏感信息

`TrafficLog` 会记录完整请求/响应体、头、插件日志。如果请求或响应中包含敏感内容（如用户隐私、密钥），会写入数据库。

`src/main/java/com/xenoamess/damning_proxy/entity/TrafficLog.java:11`

### 脱敏处理

- 请求头中的 `Authorization` 在落库前会被替换为 `Bearer ***`，不会明文保存上游 Token。
- 其他自定义头、请求体、响应体仍原样记录，若包含敏感信息请自行在插件中脱敏。

### 建议

- 定期清理日志。
- 必要时在插件中对敏感字段脱敏后再记录（当前截断逻辑不影响敏感内容）。

---

## 6. H2 文件数据库

默认使用文件型 H2，数据文件位于用户主目录：

```text
${user.home}/.damning-proxy/data.mv.db
```

### 建议

- 生产环境切换到 PostgreSQL/MySQL。
- 备份 H2 数据文件时先停止服务，避免文件损坏。

---

## 安全改进路线图（建议）

| 优先级 | 改进项 |
|---|---|
| 高 | 为 `/api/*` 增加认证 |
| 高 | 限制 CORS 来源 |
| 中 | 插件执行沙箱化 |
| 中 | 敏感配置加密或走环境变量 |
| 低 | 日志敏感字段自动脱敏 |
