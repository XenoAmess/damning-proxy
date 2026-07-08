[中文版](03-configuration.md)

# 03 Configuration

> Last updated: 2026-06-21  
> Source version: current workspace

## Main Configuration File

`src/main/resources/application.properties`

---

## Core Configuration Items

### HTTP Service

```properties
quarkus.http.port=12360
quarkus.http.host=0.0.0.0
```

- Service listening port and bind address.
- Can be overridden via environment variables `QUARKUS_HTTP_PORT`, `QUARKUS_HTTP_HOST`, or system properties.

---

### Database

```properties
quarkus.datasource.db-kind=h2
quarkus.datasource.jdbc.url=jdbc:h2:file:${user.home}/.damning-proxy/data;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
quarkus.datasource.username=sa
quarkus.datasource.password=sa
quarkus.hibernate-orm.database.generation=update
quarkus.hibernate-orm.log.sql=false
```

| Configuration Item | Description |
|---|---|
| `db-kind` | Database type, default H2 |
| `jdbc.url` | H2 file database path; `${user.home}` is the user's home directory |
| `username` / `password` | H2 default credentials |
| `database.generation=update` | Auto-update schema |
| `log.sql=false` | Disable SQL logging |

Switch to PostgreSQL example:

```properties
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/damning_proxy
quarkus.datasource.username=damning
quarkus.datasource.password=secret
quarkus.hibernate-orm.database.generation=update
```

Switching to MySQL is similar; add the corresponding JDBC dependency.

---

### CORS

```properties
quarkus.http.cors=true
quarkus.http.cors.origins=*
quarkus.http.cors.methods=GET,POST,PUT,DELETE,OPTIONS
quarkus.http.cors.headers=accept,authorization,content-type,x-requested-with
```

- CORS is enabled by default, allowing any origin.
- In production, restrict `origins`.

---

### Logging

```properties
quarkus.log.level=INFO
quarkus.log.category."com.xenoamess.damning_proxy".level=DEBUG
```

- Global log level `INFO`.
- Project package log level `DEBUG`.

---

### Proxy Custom Configuration

```properties
damning-proxy.default-timeout-ms=30000
```

- Default upstream connection timeout in milliseconds.
- A single Profile's `timeoutMs` can override this.

`src/main/resources/application.properties:27`

---

### Log Truncation Configuration

Controls the maximum character length of each field stored in TrafficLog; content beyond the limit is truncated and `...[truncated]` is appended.

```properties
damning-proxy.log.max-body-length=1073741824
damning-proxy.log.max-headers-length=2000
damning-proxy.log.max-plugin-logs-length=5000
damning-proxy.log.max-friendly-snapshots-length=8000
```

| Configuration Item | Default | Description |
|---|---|---|
| `damning-proxy.log.max-body-length` | `1073741824` | Maximum stored length of requestBody / responseBody |
| `damning-proxy.log.max-headers-length` | `2000` | Maximum stored length of requestHeaders / responseHeaders |
| `damning-proxy.log.max-plugin-logs-length` | `5000` | Maximum stored length of pluginLogs |
| `damning-proxy.log.max-friendly-snapshots-length` | `8000` | Maximum stored length of friendlyPluginSnapshots |

### Log Retention Configuration

```properties
damning-proxy.log.max-count=100000
```

- Maximum number of log entries; oldest logs are automatically deleted when exceeded.
- Checked every 100 writes.

### Rate Limiting

```properties
damning-proxy.rate-limit.max-requests=60
damning-proxy.rate-limit.window-seconds=60
```

| Configuration Item | Default | Description |
|---|---|---|
| `damning-proxy.rate-limit.max-requests` | `60` | Maximum requests allowed per window per instance slug |
| `damning-proxy.rate-limit.window-seconds` | `60` | Sliding window duration (seconds) |

`src/main/resources/application.properties:27`

---

## Environment Variable Overrides

Quarkus supports overriding configuration via environment variables: replace dots `.` with underscores `_` and uppercase.

Example:

```bash
export QUARKUS_HTTP_PORT=8080
export QUARKUS_DATASOURCE_JDBC_URL=jdbc:h2:file:/data/damning-proxy/data
export DAMNING_PROXY_DEFAULT_TIMEOUT_MS=60000
```

---

## Test Configuration

Tests use a separate config: `src/test/resources/application.properties`

```properties
quarkus.datasource.db-kind=h2
quarkus.datasource.jdbc.url=jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
quarkus.hibernate-orm.database.generation=drop-and-create
```

- Uses in-memory H2 to avoid file lock issues.
- Drops and recreates tables on every test run.

---

## Configuration Quick Reference

| Configuration Item | Default | Description |
|---|---|---|
| `quarkus.http.port` | `12360` | Service port |
| `quarkus.http.host` | `0.0.0.0` | Bind address |
| `quarkus.datasource.db-kind` | `h2` | Database type |
| `quarkus.datasource.jdbc.url` | `${user.home}/.damning-proxy/data` | JDBC URL |
| `quarkus.hibernate-orm.database.generation` | `update` | Auto create/update schema |
| `quarkus.http.cors` | `true` | Whether CORS is enabled |
| `quarkus.http.cors.origins` | `*` | Allowed CORS origins |
| `quarkus.log.level` | `INFO` | Global log level |
| `damning-proxy.default-timeout-ms` | `30000` | Default upstream timeout |
| `damning-proxy.log.max-body-length` | `1073741824` | Maximum stored length of request/response body |
| `damning-proxy.log.max-headers-length` | `2000` | Maximum stored length of request/response headers |
| `damning-proxy.log.max-plugin-logs-length` | `5000` | Maximum stored length of pluginLogs |
| `damning-proxy.log.max-friendly-snapshots-length` | `8000` | Maximum stored length of friendlyPluginSnapshots |
| `damning-proxy.log.max-count` | `100000` | Maximum log entries; old logs auto-deleted |
| `damning-proxy.rate-limit.max-requests` | `60` | Max requests per window per instance |
| `damning-proxy.rate-limit.window-seconds` | `60` | Rate-limit sliding window seconds |
