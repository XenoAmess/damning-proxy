# 02 运行方式

> 最后更新：2026-06-17  
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

## Native Image 方式运行

构建：

```bash
mvn clean package -Pnative -DskipTests
```

运行：

```bash
./target/damning-proxy-1.0-SNAPSHOT-runner
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

使用 `nohup`：

```bash
nohup java -jar target/quarkus-app/quarkus-run.jar > damning-proxy.log 2>&1 &
```

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

## Docker 运行（可选）

项目当前未提供 Dockerfile，可手动创建：

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/quarkus-app /app/quarkus-app
EXPOSE 12360
CMD ["java", "-jar", "/app/quarkus-app/quarkus-run.jar"]
```

构建并运行：

```bash
docker build -t damning-proxy .
docker run -p 12360:12360 damning-proxy
```

注意挂载数据卷以持久化 H2 数据库。
