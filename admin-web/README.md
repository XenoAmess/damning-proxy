# 大明proxy 管理后台

基于 Vue 3 + Vite + Element Plus 开发。

## 开发

```bash
cd admin-web
npm install
npm run dev
```

开发服务器会代理 `/api` 到 `http://localhost:12360`。

## 构建

```bash
npm run build
```

构建产物输出到 `../src/main/resources/META-INF/resources/admin/`，Quarkus 会自动提供静态资源。

## Maven 构建

根项目已配置 `frontend-maven-plugin`，执行 `mvn package` 时会自动安装 Node、npm 依赖并构建前端。
