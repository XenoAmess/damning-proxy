[Chinese Version](dependabot-optimization-notes.zh.md)

# Dependabot Optimization Notes

Date: 2026-07-01

## What changed

1. **`.github/dependabot.yml`** — optimized settings:
   - Lowered `open-pull-requests-limit` from `100` to `10` (maven) and `5` (npm, github-actions).
   - Unified all ecosystems to weekly schedule on Monday 04:00 Asia/Shanghai.
   - Added `target-branch: master` to every ecosystem entry.
   - Added `commit-message.prefix` (`build(deps)` / `build(deps-dev)` / `ci`) for consistent commit/PR titles.
   - Kept existing labels (`dependencies`, `java`, `javascript`, `ci`).
   - No `groups:` block — one dependency per PR keeps diffs reviewable and bisectable.

2. **`.github/workflows/auto-merge.yml`** — new workflow:
   - Fires on every `pull_request` event authored by `dependabot[bot]` or `app/dependabot`.
   - Uses `dependabot/fetch-metadata@v2` to read update metadata.
   - Auto-approves and enables auto-merge for:
     - `semver-patch`
     - `semver-minor`
     - `semver-major` **only** for `dependabot/github_actions/*` PRs
   - Leaves `semver-major` maven/npm PRs for human review.
   - Uses a `MYTOKEN` secret (PAT/OAuth with `workflow` scope) because `GITHUB_TOKEN` cannot enable auto-merge on workflow-modifying PRs.

3. **Repository settings**:
   - Enabled `allow_auto_merge`.
   - Set branch protection on `master` to require the `test` status check with `strict: true`.
   - Preserved `required_linear_history: true`.

4. **Secrets**:
   - Added `MYTOKEN` to the Dependabot secret namespace (`gh secret set MYTOKEN --app dependabot`).
   - Token is the repo admin's OAuth token (`gho_*`), verified to have `workflow` scope via smoke test.

## Verification performed

- `gh auth status` confirmed admin OAuth token.
- `gh api repos/XenoAmess/damning-proxy --jq '.allow_auto_merge'` returned `true`.
- Branch protection API returned `required_status_checks.contexts: ["test"]`.
- `gh secret list --app dependabot` lists `MYTOKEN`.
- Smoke test: `gh pr merge 26 --auto --rebase` succeeded without workflow-permission error.

## Handling existing backlog

At optimization time there were 9 open Dependabot PRs. After pushing the workflow files, `@dependabot rebase` was commented on the remaining open PRs to trigger the new `auto-merge` workflow on each one.

## Ongoing notes

- If the CI matrix or job name in `.github/workflows/ci.yml` changes, re-run the branch-protection step with the new actual check name(s).
- If Dependabot login migration changes again, update the `if:` condition in `auto-merge.yml`.
- Keep `MYTOKEN` valid; if the OAuth token is revoked, regenerate and re-set the Dependabot secret.
