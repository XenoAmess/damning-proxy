# 变更日志

> 最后更新：2026-06-20  
> 对应源码版本：当前工作区

## 2026-06-20

- 后台运行文档改为推荐使用 `screen` 管理 `mvn quarkus:dev`，避免遗留进程占用端口或 H2 锁。
- 更新代理端点文档：
  - `/v1/proxy/{instanceSlug}/chat/completions` 统一为单一路径，根据 `stream` 字段返回 JSON 或 SSE。
  - 补充 SSE 通过 `StreamingOutput` 桥接 `Multi<String>` 的实现说明。
  - 补充请求头/请求体合并优先级、上游超时禁用策略、30 秒心跳诊断。

## 2026-06-17

- 初始化 damning-proxy 知识库。
- 新增项目概览、架构设计、数据模型、代理流程、插件系统、API 文档、构建/运行/自测指南、运维文档。
- 迁移原 `doc/plan.md` 到 `docs/99-reference/plan.md`。
- 建立知识库演进约定：代码变更需同步更新对应文档。

---

## 历史变更

（待补充后续版本）
