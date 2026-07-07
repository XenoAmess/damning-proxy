[中文版](02-run.md)

# 02 Run Methods

> Last updated: 2026-07-07  
> Source version: current workspace

## Dev Mode

```bash
mvn quarkus:dev
```

- Starts at `http://localhost:12360`
- Hot reloads Java code automatically
- Builds frontend automatically
- Root path `/` redirects to `/admin/index.html`

---

## Run via JAR

Build first:

```bash
mvn clean package
```

Then run:

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

---

## Run via Native Image

Build:

```bash
mvn clean package -Pnative -DskipTests
```

Run:

```bash
./target/damning-proxy-0.1.0-runner
```

---

## Accessing the Service

| URL | Description |
|---|---|
| `http://localhost:12360/` | Home page, redirects to admin UI |
| `http://localhost:12360/admin/index.html` | Web admin UI |
| `http://localhost:12360/v1/health` | Health check |

---

## Port and Configuration

Default port is in `src/main/resources/application.properties:2`:

```properties
quarkus.http.port=12360
```

Can be overridden via environment variable:

```bash
export QUARKUS_HTTP_PORT=8080
java -jar target/quarkus-app/quarkus-run.jar
```

Or via `-D` argument:

```bash
java -Dquarkus.http.port=8080 -jar target/quarkus-app/quarkus-run.jar
```

---

## Data File Location

Default H2 file database location:

```text
${user.home}/.damning-proxy/data.mv.db
```

Corresponding config:

```properties
quarkus.datasource.jdbc.url=jdbc:h2:file:${user.home}/.damning-proxy/data;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
```

`src/main/resources/application.properties:6`

---

## Background Run Examples

### Production JAR in Background

Use `nohup`:

```bash
nohup java -jar target/quarkus-app/quarkus-run.jar > damning-proxy.log 2>&1 &
```

### Dev Mode in Background (screen recommended)

`mvn quarkus:dev` waits for terminal input by default; `nohup ... &` may still block or exit abnormally. **It is recommended to manage the dev session with `screen`**:

```bash
export JAVA_HOME=/home/xenoamess/.jdks/jdk-21.0.7+6
export PATH=$JAVA_HOME/bin:$PATH
cd /home/xenoamess/workspace/daming-proxy
screen -dmS damning-proxy /bin/bash -c \
  'mvn quarkus:dev -DskipTests > /tmp/daming-proxy-dev.log 2>&1'
```

Common management commands:

| Action | Command |
|---|---|
| View logs | `tail -f /tmp/daming-proxy-dev.log` |
| Attach to session | `screen -r damning-proxy` |
| Trigger restart inside screen | Press `Ctrl+A` then `C`, type `r` and Enter; or run `screen -S daming-proxy -X stuff $'r\n'` from outside |
| Exit and stop service | `screen -S damning-proxy -X quit` |

Notes:

- `-DskipTests` skips tests; otherwise dev mode pauses at `Tests paused` and waits for `r` to resume.
- Logs go to `/tmp/daming-proxy-dev.log`.
- Using `screen` avoids leftover Maven/Quarkus processes holding port `12360` or the H2 file lock, which can happen with `setsid` or bare backgrounding.

### Legacy `setsid` Method (fallback)

If `screen` is unavailable, you can also use `setsid` + inner backgrounding to detach from the terminal:

```bash
export JAVA_HOME=/home/xenoamess/.jdks/jdk-21.0.7+6
export PATH=$JAVA_HOME/bin:$PATH
setsid bash -c 'mvn quarkus:dev -DskipTests > /tmp/daming-proxy-dev.log 2>&1 &' </dev/null &
```

Notes:

- The inner `mvn ... &` runs Maven in the background inside a subshell; that subshell exits immediately and does not hold the opencode Bash tool's stdout pipe.
- If written as `setsid bash -c 'mvn quarkus:dev ...' </dev/null &` (without the inner `&`), the inner shell waits for `mvn` to exit and causes a command timeout.
- To stop, manually find the Java process with `pgrep -f quarkus:dev` and `kill` it.

systemd example:

```ini
[Unit]
Description=damning-proxy
After=network.target

[Service]
User=damning
WorkingDirectory=/opt/damning-proxy
ExecStart=/usr/bin/java -jar /opt/damning-proxy/target/quarkus-app/quarkus-run.jar
Restart=always

[Install]
WantedBy=multi-user.target
```

---

## Docker Run

The project now provides a `Dockerfile` and `docker-compose.yml` that multi-stage-build the Quarkus app and the Vue admin UI into a single image.

### Using docker-compose (recommended)

```bash
docker compose up -d
```

- Service listens at `http://localhost:12360`
- The H2 data file is persisted in the Docker volume `damning-data` (mounted at `/data` in the container)

Stop and remove:

```bash
docker compose down
```

### Using Docker directly

```bash
# Build image
docker build -t damning-proxy .

# Run with a data volume
docker run -d \
  --name damning-proxy \
  -p 12360:12360 \
  -v damning-data:/data \
  damning-proxy
```

### Custom port

Change the port mapping in `docker-compose.yml`:

```yaml
ports:
  - "8080:12360"
```

Or override when running `docker run`:

```bash
docker run -d \
  --name damning-proxy \
  -p 8080:12360 \
  -e QUARKUS_HTTP_PORT=12360 \
  -v damning-data:/data \
  damning-proxy
```

### Data file location

Inside the container the H2 data file is at `/data/.damning-proxy/data.mv.db`. It is persisted through the volume, so data survives container recreation.
