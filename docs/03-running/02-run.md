[English Version](02-run.en.md)

# 02 运行方式

> 最后更新：2026-07-07  
> 对应源码版本：当前工作区

## 开发模式运行

```bash
mvn quarkus:dev
```

- 启动在 `http://localhost:12360`
- 自动热加载 Java 代码
- 自动构建前端
- 根路径 `/` 会重定向到 `/admin/index.html`

---

## JAR 方式运行

先构建：

```bash
mvn clean package
```

再运行：

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

---

## Native Image 方式运行（已不支持）

本项目**不支持** GraalVM Native Image。Groovy / JavaScript 动态脚本引擎与 native-image 的闭世界假设冲突，因此不提供 native 可执行文件运行方式。请使用 JVM 方式运行：

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

---

## 访问服务

| 地址 | 说明 |
|---|---|
| `http://localhost:12360/` | 首页，自动跳转到管理后台 |
| `http://localhost:12360/admin/index.html` | Web 管理后台 |
| `http://localhost:12360/v1/health` | 健康检查 |

---

## 端口与配置

默认端口在 `src/main/resources/application.properties:2`：

```properties
quarkus.http.port=12360
```

可通过环境变量覆盖：

```bash
export QUARKUS_HTTP_PORT=8080
java -jar target/quarkus-app/quarkus-run.jar
```

或通过 `-D` 参数：

```bash
java -Dquarkus.http.port=8080 -jar target/quarkus-app/quarkus-run.jar
```

---

## 数据文件位置

默认 H2 文件数据库位于：

```text
${user.home}/.damning-proxy/data.mv.db
```

对应配置：

```properties
quarkus.datasource.jdbc.url=jdbc:h2:file:${user.home}/.damning-proxy/data;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
```

`src/main/resources/application.properties:6`

---

## 后台运行示例

### 生产 JAR 后台运行

使用 `nohup`：

```bash
nohup java -jar target/quarkus-app/quarkus-run.jar > damning-proxy.log 2>&1 &
```

### 开发模式后台运行（推荐 screen）

`mvn quarkus:dev` 默认会等待终端输入，直接 `nohup ... &` 仍可能阻塞或异常退出。**推荐使用 `screen` 管理 dev 会话**：

```bash
export JAVA_HOME=/home/xenoamess/.jdks/jdk-21.0.7+6
export PATH=$JAVA_HOME/bin:$PATH
cd /home/xenoamess/workspace/daming-proxy
screen -dmS daming-proxy /bin/bash -c \
  'mvn quarkus:dev -DskipTests > /tmp/daming-proxy-dev.log 2>&1'
```

常用管理命令：

| 操作 | 命令 |
|---|---|
| 查看日志 | `tail -f /tmp/daming-proxy-dev.log` |
| 附加到会话 | `screen -r daming-proxy` |
| 在 screen 内触发重启 | 按 `Ctrl+A` 然后 `C`，输入 `r` 回车；或从外部执行 `screen -S daming-proxy -X stuff $'r\n'` |
| 退出并停止服务 | `screen -S daming-proxy -X quit` |

说明：

- `-DskipTests` 跳过测试，否则 dev mode 启动后会暂停在 `Tests paused` 等待按 `r` 恢复。
- 日志输出到 `/tmp/daming-proxy-dev.log`。
- 使用 `screen` 可以避免 `setsid` 或裸后台启动时遗留的 Maven/Quarkus 进程占用端口 `12360` 或 H2 文件锁。

### 旧版 `setsid` 方式（备用）

如果无法使用 `screen`，也可用 `setsid` + 内层后台脱离终端：

```bash
export JAVA_HOME=/home/xenoamess/.jdks/jdk-21.0.7+6
export PATH=$JAVA_HOME/bin:$PATH
setsid bash -c 'mvn quarkus:dev -DskipTests > /tmp/daming-proxy-dev.log 2>&1 &' </dev/null &
```

注意：

- 内层 `mvn ... &` 让 Maven 在子 shell 中后台运行，该子 shell 立即退出，不会持有 opencode Bash 工具的 stdout 管道。
- 如果写成 `setsid bash -c 'mvn quarkus:dev ...' </dev/null &`（没有内层 `&`），内层 shell 会等待 `mvn` 退出，导致命令超时。
- 停止时需要手动 `pgrep -f quarkus:dev` 找到 Java 进程后 `kill`。

使用 systemd（示例）：

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

## Docker 运行

项目已提供 `Dockerfile` 和 `docker-compose.yml`，使用多阶段构建把 Quarkus 应用与 Vue 管理后台打包进一个镜像。

### 使用 docker-compose（推荐）

```bash
docker compose up -d
```

- 服务监听 `http://localhost:12360`
- H2 数据文件持久化在 Docker volume `damning-data` 中（挂载到容器 `/data`）

停止并移除：

```bash
docker compose down
```

### 使用 Docker 直接构建运行

```bash
# 构建镜像
docker build -t damning-proxy .

# 运行并挂载数据卷
docker run -d \
  --name damning-proxy \
  -p 12360:12360 \
  -v damning-data:/data \
  damning-proxy
```

### 自定义端口

修改 `docker-compose.yml` 的端口映射：

```yaml
ports:
  - "8080:12360"
```

或在 `docker run` 时覆盖：

```bash
docker run -d \
  --name damning-proxy \
  -p 8080:12360 \
  -e QUARKUS_HTTP_PORT=12360 \
  -v damning-data:/data \
  damning-proxy
```

### 数据文件位置

容器内 H2 数据文件位于 `/data/.damning-proxy/data.mv.db`，通过 volume 持久化到宿主机，避免容器重建后数据丢失。
