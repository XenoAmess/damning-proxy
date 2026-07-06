[中文版](05-troubleshooting.md)

# 05 Troubleshooting

> Last updated: 2026-06-18  
> Source version: current workspace

## Startup Issues

### Port Already in Use

```text
BindException: Address already in use
```

Solution:

```bash
mvn quarkus:dev -Dquarkus.http.port=8080
```

---

### Wrong JDK Version / Maven Uses Default JDK 25

Symptom:

```text
Java 21 is required. Tests and runtime use libraries that are not compatible with newer JDKs.
```

Or `mvn -version` shows `Java version: 25.x` even though `JAVA_HOME` points to JDK 21.

Cause: The system default `java` may be GraalVM 25; the `mvn` script selects the JDK based on `JAVA_HOME`, but if only `JAVA_HOME` is set and `PATH` still points to the JDK 25 `java`, child processes may be inconsistent.

Solution:

```bash
export JAVA_HOME=/home/xenoamess/.jdks/jdk-21.0.7+6
export PATH=$JAVA_HOME/bin:$PATH
java -version   # should show 21
mvn -version    # should show Java 21
```

Dev mode startup command:

```bash
setsid bash -c 'mvn quarkus:dev -DskipTests > /tmp/daming-proxy-dev.log 2>&1' </dev/null &
```

---

### Terminal Blocked After Startup / opencode Main Thread Hangs

Symptom: After running `mvn quarkus:dev`, opencode stops responding, or the background process exits soon after starting.

Cause: Quarkus dev mode reads stdin by default to wait for commands (e.g. `r` to resume tests, `e` to edit parameters) and pauses tests. Normal `nohup ... &` may fail because stdin is closed; `setsid bash -c 'mvn ...'` makes opencode wait for the inner shell until mvn exits.

Solution: Use `setsid` + inner backgrounding + `</dev/null`:

```bash
export JAVA_HOME=/home/xenoamess/.jdks/jdk-21.0.7+6
export PATH=$JAVA_HOME/bin:$PATH
setsid bash -c 'mvn quarkus:dev -DskipTests > /tmp/daming-proxy-dev.log 2>&1 &' </dev/null &
```

Verify:

```bash
pgrep -f quarkus:dev
tail -f /tmp/daming-proxy-dev.log
```

---

### H2 Database Lock

In dev mode, if the H2 file database is held by multiple processes, a lock error may occur.

Solution:

- Stop other processes.
- Delete `${user.home}/.damning-proxy/data.lock.db`.
- Or use in-memory H2 for temporary testing.

---

## Build Issues

### Frontend Build Failure

Common causes:

- Node/npm download failure (network issue).
- `admin-web/package-lock.json` inconsistent with `package.json`.

Solution:

```bash
cd admin-web
rm -rf node_modules package-lock.json
npm install
npm run build
```

### Native Build Failure

- Confirm GraalVM 21 is installed and `JAVA_HOME` is set.
- Check whether reflection/resource configuration files are missing.
- Check reports under `target/native-image-output/`.

---

## Proxy Issues

### 404 Instance not found

- Check whether `instanceSlug` is correct.
- Check whether the Instance is enabled.
- Check whether the Instance has been created (StartupMigration auto-creates Instances from Profiles when none exist, but if manually deleted it may not exist).

### 403 Instance disabled / Upstream profile disabled

- Check the `enabled` field of the Instance and Profile.

### 502 Upstream request failed

- Check whether the Profile `baseUrl` is correct.
- Check whether the upstream service is reachable.
- Check whether `bearerToken` is valid.
- Check the upstream request address and error message in the logs.

### Non-Streaming Endpoint Returns 400 (streaming not supported)

- The request body has `stream: true`, but the non-streaming endpoint with `Accept: application/json` was called.
- Streaming requests need `Accept: text/event-stream`.

---

## Plugin Issues

### Plugin Changes Not Taking Effect

- Groovy/JS engines cache scripts by `script` content; restart the service after modifying a plugin.

### Plugin Errors but Pipeline Continues

- `PluginExecutionService` catches plugin exceptions and logs them without interrupting subsequent plugins.
- Check plugin error messages in `/api/logs/{id}/friendly`.

### Groovy/JS Script Syntax Issues

- Test script snippets in a small `PluginEngineTest`-style case first.
- Note that Groovy's Map/List operations differ from JS.

---

## Log Issues

### Logs Not Recorded

- Check whether the request reached the proxy endpoint.
- Check whether the database connection is normal.
- Check whether `TrafficLogService` failed to commit the transaction due to an exception.

### Log Content Truncated

- `TrafficLogService` has length limits for body/header/pluginLogs/snapshots.
- Large responses are truncated; see [02-design/01-data-model.en.md](../02-design/01-data-model.en.md).

---

## Debugging Tips

1. Enable DEBUG logging:

```properties
quarkus.log.category."com.xenoamess.damning_proxy".level=DEBUG
```

2. View upstream request address: search logs for `Upstream request:`.

3. View complete exceptions: `GlobalExceptionMapper` prints all uncaught exceptions.
