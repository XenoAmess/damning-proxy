[English Version](dependabot-optimization-notes.md)

# Dependabot 优化记录

日期：2026-07-01

## 变更内容

1. **`.github/dependabot.yml`** — 优化设置：
   - 将 `open-pull-requests-limit` 从 `100` 降低为 maven `10`、npm 与 github-actions `5`。
   - 统一所有生态为每周一 Asia/Shanghai 04:00 运行。
   - 为每个生态条目添加 `target-branch: master`。
   - 添加 `commit-message.prefix`（`build(deps)` / `build(deps-dev)` / `ci`）以保持提交和 PR 标题一致。
   - 保留现有标签（`dependencies`、`java`、`javascript`、`ci`）。
   - 不使用 `groups:` 块 —— 每个依赖一个 PR，差异可审查、可二分定位。

2. **`.github/workflows/auto-merge.yml`** — 新增工作流：
   - 在作者为 `dependabot[bot]` 或 `app/dependabot` 的每个 `pull_request` 事件触发。
   - 使用 `dependabot/fetch-metadata@v2` 读取更新元数据。
   - 对以下情况自动批准并启用自动合并：
     - `semver-patch`
     - `semver-minor`
     - `semver-major` **仅**针对 `dependabot/github_actions/*` PR
   - 将 maven/npm 的 `semver-major` PR 留待人工审查。
   - 使用 `MYTOKEN` 密钥（具有 `workflow` 范围的 PAT/OAuth），因为 `GITHUB_TOKEN` 无法为修改工作流的 PR 启用自动合并。

3. **仓库设置**：
   - 启用 `allow_auto_merge`。
   - 在 `master` 分支设置分支保护，要求 `test` 状态检查且 `strict: true`。
   - 保留 `required_linear_history: true`。

4. **密钥**：
   - 在 Dependabot 密钥命名空间添加 `MYTOKEN`（`gh secret set MYTOKEN --app dependabot`）。
   - 该令牌为仓库管理员的 OAuth token（`gho_*`），已通过冒烟测试验证具备 `workflow` 范围。

## 已执行的验证

- `gh auth status` 确认管理员 OAuth 令牌。
- `gh api repos/XenoAmess/damning-proxy --jq '.allow_auto_merge'` 返回 `true`。
- 分支保护 API 返回 `required_status_checks.contexts: ["test"]`。
- `gh secret list --app dependabot` 列出 `MYTOKEN`。
- 冒烟测试：`gh pr merge 26 --auto --rebase` 成功，未出现工作流权限错误。

## 处理现有积压

优化时共有 9 个开放的 Dependabot PR。推送工作流文件后，在剩余的开放 PR 上评论 `@dependabot rebase`，以触发每个 PR 上的新 `auto-merge` 工作流。

## 后续注意事项

- 如果 `.github/workflows/ci.yml` 中的 CI 矩阵或任务名称发生变化，请使用新的实际检查名称重新运行分支保护配置步骤。
- 如果 Dependabot 登录标识再次迁移，请更新 `auto-merge.yml` 中的 `if:` 条件。
- 保持 `MYTOKEN` 有效；如果 OAuth 令牌被撤销，请重新生成并重新设置 Dependabot 密钥。
