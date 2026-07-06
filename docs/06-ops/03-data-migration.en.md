[中文版](03-data-migration.md)

# 03 Data Migration

> Last updated: 2026-06-17  
> Source version: current workspace

## Startup Migration

`src/main/java/com/xenoamess/damning_proxy/migration/StartupMigration.java:23`

On startup, `StartupMigration.onStart()` runs:

1. **Ensure sample plugins exist**:
   - `Damining Warhammer Prompt (Groovy)`
   - `Damining Warhammer Prompt (JS)`
   - Both append a fixed system prompt in the `REQUEST` phase.

2. **Auto-create default instances** (only when there is no `ProxyInstance` in the database):
   - Create one instance for each `ProxyProfile`.
   - Instance `slug` = profile.slug.
   - Instance `pluginGroupId` = an auto-created plugin group containing all enabled plugins.
   - Instance `profileId` = profile.id.

---

## Migration Behavior Details

### Sample Plugins

- If a plugin with the same name already exists, update its description/language/executionPhase/script/enabled.
- If it does not exist, create a new plugin.

### Sample Plugin Groups

- Two plugin groups: `sample-groovy` and `sample-js`.
- On every startup, if they exist, the old groups are deleted and recreated.

### Default Instances

- Only runs when `instanceRepository.listAll()` is empty.
- If instances already exist, new Profiles will not automatically get instances.

---

## Manual Migration Scenarios

### H2 → PostgreSQL/MySQL

1. Update the database configuration in `application.properties`.
2. Add the corresponding JDBC dependency to `pom.xml`.
3. Start the service; `quarkus.hibernate-orm.database.generation=update` will create tables automatically.
4. To migrate old data, use database tools to export/import.

### Schema Changes

When entity fields change:

- In development, set `quarkus.hibernate-orm.database.generation=drop-and-create` to recreate tables (data will be lost).
- In production, use migration tools such as Flyway/Liquibase to manage schema changes.

---

## Notes

- Startup migration is idempotent (sample plugins are updated, sample groups are recreated, default instances are created only when empty).
- Auto-created default instance and plugin group names are fixed for easy identification.
- Deleting all instances and restarting the service triggers default instance creation again.
