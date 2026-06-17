# 03 数据迁移

> 最后更新：2026-06-17  
> 对应源码版本：当前工作区

## 启动迁移

`src/main/java/com/xenoamess/damning_proxy/migration/StartupMigration.java:23`

服务启动时会执行 `StartupMigration.onStart()`：

1. **确保示例插件存在**：
   - `大明战锤提示词（Groovy）`
   - `大明战锤提示词（JS）`
   - 两者都在 `REQUEST` 阶段追加固定 system prompt。

2. **自动创建默认实例**（仅当数据库中没有任何 `ProxyInstance` 时）：
   - 为每个 `ProxyProfile` 创建一个实例。
   - 实例 `slug` = profile.slug。
   - 实例 `pluginGroupId` = 自动创建的包含所有启用插件的插件组。
   - 实例 `profileId` = profile.id。

---

## 迁移行为细节

### 示例插件

- 如果数据库中已存在同名插件，则更新其 description/language/executionPhase/script/enabled。
- 如果不存在，则创建新插件。

### 示例插件组

- `sample-groovy`、`sample-js` 两个插件组。
- 每次启动时若存在则删除旧组并重新创建。

### 默认实例

- 仅在 `instanceRepository.listAll()` 为空时执行。
- 如果已有实例，不会自动为新增 Profile 创建实例。

---

## 手动迁移场景

### H2 → PostgreSQL/MySQL

1. 修改 `application.properties` 中的数据库配置。
2. 添加对应 JDBC 依赖到 `pom.xml`。
3. 启动服务，`quarkus.hibernate-orm.database.generation=update` 会自动建表。
4. 如需迁移旧数据，使用数据库工具导出/导入。

### 结构变更

当实体字段变更时：

- 开发环境可设置 `quarkus.hibernate-orm.database.generation=drop-and-create` 重新建表（会丢失数据）。
- 生产环境应使用 Flyway/Liquibase 等迁移工具管理 schema 变更。

---

## 注意事项

- 启动迁移是幂等的（示例插件会更新，示例组会重建，默认实例仅在空时创建）。
- 自动创建的默认实例和插件组名称固定，便于识别。
- 删除所有实例后重启服务，会再次触发默认实例创建。
