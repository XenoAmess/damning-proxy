# damning-proxy 知识库

> 最后更新：2026-06-17  
> 对应源码版本：当前工作区（未打 tag）

本知识库是 [damning-proxy（大明proxy）](../README.md) 的文档化中心，覆盖项目定位、架构设计、API、构建运行、自测、运维与开发指南。

---

## 目录

### 01 项目概览
- [01 项目介绍](01-overview/01-introduction.md)
- [02 整体架构](01-overview/02-architecture.md)
- [03 术语表](01-overview/03-glossary.md)
- [04 知识库演进约定](01-overview/04-evolution-notes.md)

### 02 设计文档
- [01 数据模型](02-design/01-data-model.md)
- [02 代理请求处理流程](02-design/02-proxy-flow.md)
- [03 插件系统](02-design/03-plugin-system.md)
- [04 安全提示](02-design/04-security-notes.md)

### 03 运行与构建
- [01 构建方式](03-running/01-build.md)
- [02 运行方式](03-running/02-run.md)
- [03 配置说明](03-running/03-configuration.md)
- [04 自测方式](03-running/04-self-test.md)
- [05 常见问题排查](03-running/05-troubleshooting.md)

### 04 API 文档
- [01 代理端点](04-api/01-proxy-api.md)
- [02 管理后台端点](04-api/02-admin-api.md)
- [03 错误处理](04-api/03-error-handling.md)

### 05 使用指南
- [01 快速开始](05-guides/01-quickstart.md)
- [02 插件开发指南](05-guides/02-plugin-development.md)
- [03 接入 OpenCode](05-guides/03-connect-opencode.md)

### 06 运维
- [01 流量日志](06-ops/01-logging.md)
- [02 健康检查与监控](06-ops/02-monitoring.md)
- [03 数据迁移](06-ops/03-data-migration.md)

### 99 参考资料
- [变更日志](99-reference/changelog.md)
- [改造实施计划](99-reference/plan.md)
- [优化与改进清单](99-reference/improvement-checklist.md)

---

## 快速定位

| 我想了解 | 跳转 |
|---|---|
| 如何运行开发环境 | [03-running/02-run.md](03-running/02-run.md) |
| 如何构建 JAR / Native Image | [03-running/01-build.md](03-running/01-build.md) |
| 如何跑测试 | [03-running/04-self-test.md](03-running/04-self-test.md) |
| 如何写一个插件 | [05-guides/02-plugin-development.md](05-guides/02-plugin-development.md) |
| API 清单 | [04-api/](04-api/) |
| 数据模型说明 | [02-design/01-data-model.md](02-design/01-data-model.md) |
| 配置项说明 | [03-running/03-configuration.md](03-running/03-configuration.md) |

---

## 维护说明

本知识库随代码演进同步更新。任何涉及实体字段、API 路径、构建流程、插件 API 的代码变更，都应同步修改对应文档，详见 [04 知识库演进约定](01-overview/04-evolution-notes.md)。
