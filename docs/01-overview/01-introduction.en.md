[中文版](01-introduction.md)

# 01 Project Introduction

> Last updated: 2026-06-21  
> Source version: current workspace

## What is damning-proxy

**damning-proxy (大明proxy)** is an OpenAI-protocol proxy server built on Java 21 + Quarkus.

It exposes OpenAI-compatible endpoints (`/v1/proxy/{instanceSlug}/chat/completions`, `/v1/proxy/{instanceSlug}/models`), forwards requests to configured upstream OpenAI sources, and modifies inbound/outbound messages in sequence via **Groovy / JavaScript plugins**, enabling custom features such as model mapping, request rewriting, response replacement, and log auditing.

Project source entry: `README.md`

---

## Core Capabilities

- **OpenAI Protocol Proxy**: Exposes OpenAI-compatible endpoints and forwards to configured upstream sources.
- **Instance-based Routing**: External callers use an "Instance"; each instance binds to one upstream Profile and one plugin group.
- **Upstream Profile Management**: Independently manage upstream source URL, Bearer Token, custom headers, timeouts, etc.
- **Plugin Group Management**: Pick plugins from the plugin library, specify execution order, priority, and enabled status.
- **Plugin Library Management**: Centrally manage Groovy / JavaScript plugin scripts.
- **Full Traffic Logging**: Records request/response per instance, viewable and cleanable via the web UI.
- **Web Admin Console**: Local management UI based on Vue 3 + Vite + Element Plus.
- **GraalVM Native Image Support**: Can be built into a native executable.

---

## Technology Stack

| Technology | Version | Description |
|---|---|---|
| Java | 21 | Runtime and compile requirement; JDK 22+ may be incompatible due to dependency constraints |
| Quarkus | 3.36.3 | Backend framework |
| Maven | 3.9+ | Build tool |
| H2 | - | Default file-based database; easy to switch to PostgreSQL/MySQL |
| Hibernate ORM Panache | - | Data access abstraction |
| Vert.x Web Client | - | Upstream HTTP requests |
| Groovy JSR-223 | 3.0.22 | Groovy plugin engine |
| GraalJS / GraalVM Polyglot | 24.1.1 | JavaScript plugin engine |
| Vue | 3 | Frontend framework |
| Vite | 8 | Frontend build tool |
| Element Plus | 2 | UI component library |

For detailed configuration, see [03 Configuration](../03-running/03-configuration.en.md).

---

## Quick Links

- Get running in 5 minutes: [Quick Start](../05-guides/01-quickstart.en.md)
- Full build/run/self-test instructions: [03 Running and Building](../03-running/)
- Plugin development examples: [Plugin Development Guide](../05-guides/02-plugin-development.en.md)
- API quick reference: [04 API Documentation](../04-api/)

---

## Project Structure Overview

```text
src/main/java/com/xenoamess/damning_proxy/
├── api/                  # REST APIs (proxy, admin, home)
│   ├── admin/            # Admin REST APIs
│   ├── HomeResource.java
│   └── HealthResource.java
├── entity/               # Domain entities
├── plugin/               # Plugin context and execution engine
│   └── engine/           # Groovy / JS engines
├── proxy/                # OpenAI proxy core
├── repository/           # Repository interfaces
│   └── panache/          # Panache H2 implementations
└── service/              # Business services (logging service)

src/main/resources/
├── application.properties
└── META-INF/resources/admin/   # Frontend build output

admin-web/                # Vue 3 admin UI source
```

For the full architecture description, see [02 Architecture](02-architecture.en.md).

---

## License

MIT (see root README for details).
