# Agent Notes for damning-proxy

## Environment

- **Java 21 is mandatory** — the Maven enforcer plugin rejects newer JDKs. The default system `java` may be JDK 25, so always set:
  ```bash
  export JAVA_HOME=/home/xenoamess/.jdks/jdk-21.0.7+6
  export PATH=$JAVA_HOME/bin:$PATH
  ```
- No `mvnw`; use the system `mvn` (3.9+).

## Build & Run

| Goal | Command |
|------|---------|
| Dev mode with live reload | `$JAVA_HOME/bin/mvn quarkus:dev` |
| Dev mode background (no blocking) | `setsid bash -c 'mvn quarkus:dev -DskipTests > /tmp/daming-proxy-dev.log 2>&1' </dev/null &` |
| Run tests | `$JAVA_HOME/bin/mvn test` |
| Build runnable JAR + admin UI | `$JAVA_HOME/bin/mvn clean package -DskipTests` |
| Run the built JAR | `$JAVA_HOME/bin/java -jar target/quarkus-app/quarkus-run.jar` |
| Native image (needs GraalVM 21) | `$JAVA_HOME/bin/mvn clean package -Pnative` then `./target/damning-proxy-1.0-SNAPSHOT-runner` |

- Dev server listens on `http://localhost:12360`. Root `/` redirects to the admin UI; admin UI is also at `/admin/index.html`.
- Maven auto-builds the Vue admin UI via `frontend-maven-plugin` (Node 20.15.1, npm 10.7.0). Vite 8 emits engine warnings but the build succeeds.

## Frontend-only dev

```bash
cd admin-web
npm install
npm run dev
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

- After finishing any task, stage and commit all changes with a concise, repo-style commit message:
  ```bash
  git add .
  git commit -m "<message>"
  ```
- After finishing any task, sync relevant documentation and the knowledge base under `docs/` if the change affects architecture, behavior, configuration, or conventions.
- After finishing any task that changes backend APIs, frontend admin UI, or any runtime behavior, **restart the running service** so the new code takes effect. Use dev mode with live reload (`$JAVA_HOME/bin/mvn quarkus:dev`) to minimize downtime, or stop and restart the existing server process. When restarting via opencode Bash, always use the background command and avoid waiting on the process.

## Plugin Context Quirk

- Plugins receive a mutable `context`. `getRequestBody()` returns the live body object; mutating it in-place and calling `setRequestBody()` is the normal pattern. Snapshots are deep-copied so the friendly log shows a true before/after diff.

## opencode.json

- `opencode.json` registers this project as a local AI provider for OpenCode itself. It is not required for building or running the app.
