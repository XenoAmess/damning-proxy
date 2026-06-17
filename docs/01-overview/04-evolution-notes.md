# 04 知识库演进约定

> 最后更新：2026-06-17  
> 对应源码版本：当前工作区

本知识库不是一次性文档，而是随代码演进而持续维护的知识中心。所有开发者在对代码做以下类型的改动时，应同步更新本文档。

---

## 哪些改动必须更新知识库

| 改动类型 | 必须更新的文档位置 |
|---|---|
| 新增/修改/删除实体字段 | [02-design/01-data-model.md](../02-design/01-data-model.md) |
| 新增/修改/删除 REST API 路径或参数 | [04-api/](../04-api/) 下对应文件 |
| 修改请求/响应处理流程（含流式） | [02-design/02-proxy-flow.md](../02-design/02-proxy-flow.md) |
| 修改插件上下文 API | [02-design/03-plugin-system.md](../02-design/03-plugin-system.md)、[05-guides/02-plugin-development.md](../05-guides/02-plugin-development.md) |
| 修改构建方式、依赖、JDK 版本 | [03-running/01-build.md](../03-running/01-build.md) |
| 修改运行方式、端口、配置项 | [03-running/02-run.md](../03-running/02-run.md)、[03-running/03-configuration.md](../03-running/03-configuration.md) |
| 修改测试命令或测试资源 | [03-running/04-self-test.md](../03-running/04-self-test.md) |
| 修改日志结构或日志字段 | [06-ops/01-logging.md](../06-ops/01-logging.md) |
| 修改启动迁移逻辑、示例数据 | [06-ops/03-data-migration.md](../06-ops/03-data-migration.md) |
| 新增前端页面或调整导航 | [01-overview/02-architecture.md](../01-overview/02-architecture.md) 与相关指南 |

---

## 引用代码位置的规范

文档中引用源码时，统一使用「文件路径:行号」格式，例如：

```text
OpenAiProxyService.chatCompletions()  [src/main/java/com/xenoamess/damning_proxy/proxy/OpenAiProxyService.java:105]
```

这样便于：
- 读者在 IDE 中快速跳转到源码。
- 后续通过脚本/搜索快速核对文档中的代码引用是否仍然有效。

---

## 每个文档的元信息

每个 Markdown 文件顶部应包含：

```markdown
> 最后更新：YYYY-MM-DD  
> 对应源码版本：当前工作区 / commit hash / tag
```

进行重大更新时，同时更新 [99-reference/changelog.md](../99-reference/changelog.md)。

---

## 内部链接约定

- 使用相对路径链接到同目录或兄弟目录文档，例如 `[数据模型](../02-design/01-data-model.md)`。
- 根目录 README 使用绝对路径 `/home/xenoamess/workspace/daming-proxy/README.md` 或相对路径 `../README.md`。
- 源码文件使用绝对路径 `/home/xenoamess/workspace/daming-proxy/src/main/java/...`，便于在文件系统中定位。

---

## 版本归档

当发布 tag 或发生重大架构变更时，可在 `99-reference/` 下保留历史版本快照，命名格式为 `architecture-v1.md`、`api-v1.md` 等，避免旧链接失效。
