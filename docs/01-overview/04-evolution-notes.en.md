[中文版](04-evolution-notes.md)

# 04 Knowledge Base Evolution Conventions

> Last updated: 2026-06-17  
> Source version: current workspace

This knowledge base is not a one-time document, but a knowledge center maintained continuously as the code evolves. All developers should sync updates to this documentation when making the following types of code changes.

---

## Which Changes Require Knowledge Base Updates

| Change Type | Required Documentation Update |
|---|---|
| Add/modify/delete entity fields | [02-design/01-data-model.md](../02-design/01-data-model.en.md) |
| Add/modify/delete REST API paths or parameters | Corresponding file under [04-api/](../04-api/) |
| Modify request/response processing flow (including streaming) | [02-design/02-proxy-flow.md](../02-design/02-proxy-flow.en.md) |
| Modify plugin context API | [02-design/03-plugin-system.md](../02-design/03-plugin-system.en.md), [05-guides/02-plugin-development.md](../05-guides/02-plugin-development.en.md) |
| Modify build methods, dependencies, or JDK versions | [03-running/01-build.md](../03-running/01-build.en.md) |
| Modify runtime methods, ports, or configuration items | [03-running/02-run.md](../03-running/02-run.en.md), [03-running/03-configuration.md](../03-running/03-configuration.en.md) |
| Modify test commands or test resources | [03-running/04-self-test.md](../03-running/04-self-test.en.md) |
| Modify log structure or log fields | [06-ops/01-logging.md](../06-ops/01-logging.en.md) |
| Modify startup migration logic or sample data | [06-ops/03-data-migration.md](../06-ops/03-data-migration.en.md) |
| Add frontend pages or adjust navigation | [01-overview/02-architecture.md](../01-overview/02-architecture.en.md) and related guides |

---

## Code Reference Conventions

When referencing source code in documentation, use the `file path:line number` format consistently, for example:

```text
OpenAiProxyService.chatCompletions()  [src/main/java/com/xenoamess/damning_proxy/proxy/OpenAiProxyService.java:105]
```

This makes it easy to:
- Let readers quickly jump to the source in their IDE.
- Later verify via scripts/searches whether code references in the documentation are still valid.

---

## Metadata for Each Document

Each Markdown file should include at the top:

```markdown
> Last updated: YYYY-MM-DD  
> Source version: current workspace / commit hash / tag
```

When making significant updates, also update [99-reference/changelog.md](../99-reference/changelog.md).

---

## Internal Link Conventions

- Use relative paths to link to documents in the same or sibling directories, e.g. `[Data Model](../02-design/01-data-model.md)`.
- Root README uses absolute path `/home/xenoamess/workspace/daming-proxy/README.md` or relative path `../README.md`.
- Source files use absolute path `/home/xenoamess/workspace/daming-proxy/src/main/java/...` for easy filesystem navigation.

---

## Version Archiving

When releasing a tag or making major architectural changes, keep historical snapshots under `99-reference/` with names like `architecture-v1.md`, `api-v1.md`, etc., to avoid breaking old links.
