[English Version](AGENTS.en.md)

# Agent Notes for damning-proxy

## Environment

- **Java 21 minimum** — the Maven enforcer requires 21+. JDK 25 is confirmed working.
  ```bash
  export JAVA_HOME=/home/xenoamess/.jdks/jdk-21.0.7+6
  export PATH=$JAVA_HOME/bin:$PATH
  ```
- No `mvnw`; use the system `mvn` (3.9+).

## Build & Run

- **Prefer screen for dev mode**: `setsid` and direct backgrounding can leave stale Maven/Quarkus processes that hold port `12360` or the H2 file lock. Use the `screen` command above and manage the session with `screen -r` / `screen -S daming-proxy -X quit`.
- If startup fails with `Port 12360 seems to be in use` or H2 `Database may be already in use`, kill all stale `daming-proxy`/`quarkus` Java processes (`ps -ef | grep quarkus`) and restart.
- After deleting `~/.damning-proxy/data.mv.db`, recreate the `minimax` instance/profile if needed (the migration only creates sample plugins/groups, not instances).

| Goal | Command |
|------|---------|
| Dev mode with live reload | `$JAVA_HOME/bin/mvn quarkus:dev` |
| Dev mode in screen (recommended for opencode) | `screen -dmS daming-proxy /bin/bash -c 'export JAVA_HOME=/home/xenoamess/.jdks/jdk-21.0.7+6; export PATH=$JAVA_HOME/bin:$PATH; cd /home/xenoamess/workspace/daming-proxy; mvn quarkus:dev -DskipTests > /tmp/daming-proxy-dev.log 2>&1'` |
| Attach to screen session | `screen -r daming-proxy` |
| Send restart to screen dev mode | `screen -S daming-proxy -X stuff $'r\n'` |
| Kill screen dev server | `screen -S daming-proxy -X quit` |
| Dev mode background (no blocking) | `setsid bash -c 'mvn quarkus:dev -DskipTests > /tmp/daming-proxy-dev.log 2>&1 &' </dev/null &` |
| Run tests | `$JAVA_HOME/bin/mvn test` |
| Build runnable JAR + admin UI | `$JAVA_HOME/bin/mvn clean package -DskipTests` |
| Run the built JAR | `$JAVA_HOME/bin/java -jar target/quarkus-app/quarkus-run.jar` |
| Native image | **不支持**。Groovy / JavaScript 动态脚本引擎与 native-image 的闭世界假设冲突，无法可靠运行。项目只提供 JVM 构建产物。 |

- Dev server listens on `http://localhost:12360`. Root `/` redirects to the admin UI; admin UI is also at `/admin/index.html`.
- Maven auto-builds the Vue admin UI via `frontend-maven-plugin` (Node 22.12, npm 10.9). Vite 8 emits engine warnings but the build succeeds.
- CI uses `pnpm` (via `corepack`) instead of npm because npm 10.x crashes with 'Exit handler never called!' on GitHub Actions runners.

## Frontend-only dev

```bash
cd admin-web
corepack enable pnpm
pnpm install
pnpm run dev
```

Vite dev server proxies `/api` to `http://localhost:12360`. Requires a backend running on that port.

## Testing

- Tests use an in-memory H2 database (`src/test/resources/application.properties` with `drop-and-create`).
- Run a single test class: `$JAVA_HOME/bin/mvn test -Dtest=ProxyApiTest`
- No separate lint/typecheck steps are configured; rely on `mvn test` and `mvn package`.

## Data & State

- Dev/prod default DB: H2 file at `${user.home}/.damning-proxy/data.mv.db`.
- Hibernate auto-updates schema (`quarkus.hibernate-orm.database.generation=update`).
- If startup behaves weirdly (e.g. sample plugin groups empty or constraint violations), stop the server and delete `~/.damning-proxy/data.mv.db`, then restart.
- `StartupMigration` runs on every startup and auto-creates sample plugins/groups; it also migrates any legacy `ProxyProfile` rows into `ProxyInstance` + `PluginGroup` rows on first run.

## API Layout

- Admin REST APIs are under `/api/` (e.g. `/api/plugin-groups`, `/api/instances`, `/api/profiles`, `/api/logs`). **Not** `/api/admin/`.
- Proxy endpoints are under `/v1/proxy/{instanceSlug}/chat/completions` and `/v1/proxy/{instanceSlug}/models`.

## Project Structure

- `src/main/java/com/xenoamess/damning_proxy/`
  - `api/` — REST endpoints (proxy + admin)
  - `proxy/` — OpenAI proxy core (`OpenAiProxyService`)
  - `plugin/` — plugin context, execution engine, friendly snapshots
  - `plugin/engine/` — Groovy and JavaScript engines
  - `entity/` — Panache entities
  - `repository/` + `repository/panache/` — repository interfaces and H2 impls
  - `migration/` — `StartupMigration`
  - `service/` — `TrafficLogService`
- `admin-web/` — Vue 3 + Vite admin UI source; built into `src/main/resources/META-INF/resources/admin/`.

## Agent Workflow

- After finishing any task, stage and commit all changes with a concise, repo-style commit message, then push to the remote repository:
  ```bash
  git add .
  git commit -m "<message>"
  git push
  ```
- Perform this commit-and-push automatically as part of completing the task; do not wait for the user to ask for it.
- After finishing any task, sync relevant documentation and the knowledge base under `docs/` if the change affects architecture, behavior, configuration, or conventions. All documentation changes must be kept in sync between Chinese (`*.md`) and English (`*.en.md`) versions, and the index in `docs/README.md` must list both language versions.
- After finishing any task that changes backend APIs, frontend admin UI, or any runtime behavior, **restart the running service** so the new code takes effect. Use dev mode with live reload (`$JAVA_HOME/bin/mvn quarkus:dev`) to minimize downtime, or stop and restart the existing server process. When restarting via opencode Bash, always use the background command and avoid waiting on the process.

## Plugin Context Quirk

- Plugins receive a mutable `context`. `getRequestBody()` returns the live body object; mutating it in-place and calling `setRequestBody()` is the normal pattern. Snapshots are deep-copied so the friendly log shows a true before/after diff.

## opencode.json

- `opencode.json` registers this project as a local AI provider for OpenCode itself. It is not required for building or running the app.
