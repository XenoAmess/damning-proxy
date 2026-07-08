[English Version](01-introduction.en.md)

# 01 项目介绍

> 最后更新：2026-06-21  
> 对应源码版本：当前工作区

## damning-proxy 是什么

**damning-proxy（大明proxy）** 是一个基于 Java 21 + Quarkus 的 OpenAI 协议代理服务器。

它对外暴露 OpenAI 兼容接口（`/v1/proxy/{instanceSlug}/chat/completions`、`/v1/proxy/{instanceSlug}/models`），把请求转发到配置好的上游 OpenAI 源，并通过 **Groovy / JavaScript 插件** 按顺序篡改出入报文，实现模型映射、请求改写、响应替换、日志审计等自定义功能。

项目源码入口：`README.md`

---

## 核心能力

- **OpenAI 协议代理**：对外暴露 OpenAI 兼容接口，转发到配置的上游源。
- **实例化路由**：外部用户调用的是「实例」，每个实例绑定一个上游 Profile 和一个插件组。
- **上游配置管理**：独立管理上游源地址、Bearer Token、自定义 Header、超时等。
- **插件组管理**：从插件库挑选插件，指定执行顺序、优先级和启用状态。
- **插件库管理**：集中管理 Groovy / JavaScript 插件脚本。
- **全量流量日志**：按实例记录请求/响应，支持 Web 页查看与清理。
- **Web 管理后台**：基于 Vue 3 + Vite + Element Plus 的本地管理界面。
- **明确不支持 GraalVM Native Image**：由于 Groovy / JavaScript 动态脚本引擎在 native-image 闭世界假设下无法可靠运行，项目不构建、不交付 native 可执行文件。

---

## 技术栈

| 技术 | 版本 | 说明 |
|---|---|---|
| Java | 21 | 运行与编译要求，JDK 22+ 可能因依赖限制不兼容 |
| Quarkus | 3.36.3 | 后端框架 |
| Maven | 3.9+ | 构建工具 |
| H2 | - | 默认文件型数据库，便于切换 PostgreSQL/MySQL |
| Hibernate ORM Panache | - | 数据访问抽象 |
| Vert.x Web Client | - | 上游 HTTP 请求 |
| Groovy JSR-223 | 3.0.22 | Groovy 插件引擎 |
| GraalJS / GraalVM Polyglot | 24.1.1 | JavaScript 插件引擎 |
| Vue | 3 | 前端框架 |
| Vite | 8 | 前端构建工具 |
| Element Plus | 2 | UI 组件库 |

详细配置见 [03 配置说明](../03-running/03-configuration.md)。

---

## 快速入口

- 5 分钟跑起来：[快速开始](../05-guides/01-quickstart.md)
- 查看完整构建/运行/自测：[03 运行与构建](../03-running/)
- 插件开发示例：[插件开发指南](../05-guides/02-plugin-development.md)
- API 速查：[04 API 文档](../04-api/)

---

## 项目结构速览

```text
src/main/java/com/xenoamess/damning_proxy/
├── api/                  # REST API（代理、管理、首页）
│   ├── admin/            # 管理后台 REST API
│   ├── HomeResource.java
│   └── HealthResource.java
├── entity/               # 领域实体
├── plugin/               # 插件上下文与执行引擎
│   └── engine/           # Groovy / JS 引擎
├── proxy/                # OpenAI 代理核心
├── repository/           # 仓库接口
│   └── panache/          # Panache H2 实现
└── service/              # 业务服务（日志服务）

src/main/resources/
├── application.properties
└── META-INF/resources/admin/   # 前端构建产物

admin-web/                # Vue 3 管理后台源码
```

完整架构说明见 [02 整体架构](02-architecture.md)。

---

## 许可证

MIT（详见根目录 README）。
