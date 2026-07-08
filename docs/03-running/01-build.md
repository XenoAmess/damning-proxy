[English Version](01-build.en.md)

# 01 构建方式

> 最后更新：2026-07-07  
> 对应源码版本：当前工作区

## 环境要求

| 工具 | 版本要求 | 说明 |
|---|---|---|
| JDK | 21 | 必须，maven-enforcer 限制 ``src/main/resources/application.properties:2`

---

## 构建 JAR

```bash
mvn clean package
```

构建产物：

```text
target/
├── damning-proxy-0.1.0.jar          # 普通 jar，不可直接运行
└── quarkus-app/
    └── quarkus-run.jar                     # Quarkus 可运行 jar
```

运行方式：

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

构建过程中 `frontend-maven-plugin` 会自动：

1. 在 `admin-web/` 目录安装 Node.js 和 pnpm。
2. 执行 `pnpm install --frozen-lockfile`。
3. 执行 `pnpm run build`。
4. 将构建产物输出到 `src/main/resources/META-INF/resources/admin/`。

前端构建配置：`pom.xml:178`

---

## 构建 Native Image（已不支持）

本项目**不支持**构建 GraalVM Native Image。

由于 Groovy / JavaScript 插件引擎需要在运行时动态编译和执行脚本，这与 native-image 的“闭世界”假设冲突，无法可靠地生成可工作的 native 可执行文件。因此 `pom.xml` 中不再提供 native profile，构建命令仅支持 JVM 产物。

运行请使用：

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

---

## 前端独立构建

如需单独开发前端：

```bash
cd admin-web
corepack enable pnpm
pnpm install
pnpm run dev
```

开发服务器默认代理 `/api` 到 `http://localhost:12360`，后端需同时运行。

生产构建：

```bash
cd admin-web
corepack enable pnpm
pnpm install
pnpm run build
```

产物输出到 `admin-web/dist/`，Maven 构建时会自动复制到 `src/main/resources/META-INF/resources/admin/`。

`admin-web/package.json:6`

---

## 跳过前端构建

如需跳过前端步骤（例如纯后端调试）：

```bash
mvn clean package -Dskip.frontend.build=true
```

配置项 `skip.frontend.build` 定义在 `pom.xml:22`，默认值为 `false`，通过 `frontend-maven-plugin` 的 `<skip>` 控制。跳过前端时，请确保 `src/main/resources/META-INF/resources/admin/` 中已有构建好的前端文件，否则 admin UI 无法使用。

---

## 构建命令速查

| 场景 | 命令 |
|---|---|
| 开发模式 | `mvn quarkus:dev` |
| 构建可运行 JAR | `mvn clean package` |
| 运行 JAR | `java -jar target/quarkus-app/quarkus-run.jar` |
| 构建 Native Image | `mvn clean package -Pnative` |
| 运行 Native | `./target/damning-proxy-0.1.0-runner` |
| 前端独立开发 | `cd admin-web && corepack enable pnpm && pnpm run dev` |
| 前端独立构建 | `cd admin-web && corepack enable pnpm && pnpm run build` |
| 运行前端测试 | `cd admin-web && corepack enable pnpm && pnpm test` |
| 运行测试 | `mvn test` |
| 跳过前端构建 | `mvn clean package -Dskip.frontend.build=true` |

---

## 常见构建问题

| 问题 | 可能原因 | 解决 |
|---|---|---|
| `Java 21 is required` | JDK 版本不符 | 使用 JDK 21 |
| 前端构建慢/失败 | Node/pnpm 下载慢 | 配置 Maven/Node 镜像，或手动在 `admin-web/` 运行 `pnpm install` |
| Native 构建失败 | 缺少 GraalVM | 安装 GraalVM 21 并设置 `JAVA_HOME` |
