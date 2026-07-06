[中文版](04-security-notes.md)

# 04 Security Notes

> Last updated: 2026-06-17  
> Corresponding source version: current workspace

## Important Security Disclaimer

damning-proxy is designed for **local/trusted intranet environments** by default. The current version does not include built-in admin UI authentication, and the plugin engine has near-full JVM privileges. Before deploying to production, please evaluate the following risks and take protective measures.

---

## 1. Admin UI Has No Authentication

- All `/api/*` endpoints are publicly accessible.
- Anyone who can reach the service can create/modify/delete upstream configurations, plugins, instances, and logs.

### Recommendations

- Only run locally or in a controlled intranet.
- If public access is required, place a reverse proxy in front and add authentication (e.g. Nginx Basic Auth, OAuth2 proxy).
- Future versions may consider adding Token/Session authentication for `/api/*`.

Related endpoints: [04-api/02-admin-api.en.md](../04-api/02-admin-api.en.md)

---

## 2. CORS Is Fully Open by Default

`src/main/resources/application.properties:13`

```properties
quarkus.http.cors=true
quarkus.http.cors.origins=*
quarkus.http.cors.methods=GET,POST,PUT,DELETE,OPTIONS
```

This means any webpage can call this service through the browser.

### Recommendations

- In production, restrict `quarkus.http.cors.origins` to trusted domains.
- Or disable CORS and access the admin UI through a same-origin reverse proxy.

---

## 3. Plugins Can Execute Arbitrary Code

### Groovy

`src/main/java/com/xenoamess/damning_proxy/plugin/engine/GroovyPluginEngine.java:15`

By default, Groovy scripts have full JVM access, including reading/writing files, executing system commands, and accessing the network.

### JavaScript

`src/main/java/com/xenoamess/damning_proxy/plugin/engine/JavaScriptPluginEngine.java:34`

```java
Context.newBuilder("js").allowAllAccess(true).build()
```

GraalJS is also configured with `allowAllAccess(true)`, allowing scripts to call Java classes and access the file system.

### Recommendations

- Do not run plugin scripts from untrusted sources.
- Do not hard-code secrets, passwords, or other sensitive information in plugins.
- If plugin permissions need to be restricted, introduce a sandbox or disable high-risk APIs in the future.

---

## 4. Upstream Token and Custom Headers

- `ProxyProfile.bearerToken` and `customHeaders` are sent to the upstream source as-is.
- These sensitive values are stored in the H2 database, with the database file located at `${user.home}/.damning-proxy/data.mv.db` by default.

### Recommendations

- Properly protect access to the database file.
- Consider using environment variables or a secret management system instead of storing secrets in plaintext (future improvement direction).

---

## 5. Logs May Contain Sensitive Information

`TrafficLog` records the full request/response body, headers, and plugin logs. If the request or response contains sensitive content (e.g. user privacy data, keys), it will be written to the database.

`src/main/java/com/xenoamess/damning_proxy/entity/TrafficLog.java:11`

### Desensitization

- The `Authorization` request header is replaced with `Bearer ***` before persistence, so the upstream token is not stored in plaintext.
- Other custom headers, request bodies, and response bodies are still recorded as-is; if they contain sensitive information, please desensitize them in plugins yourself.

### Recommendations

- Clean up logs regularly.
- If necessary, desensitize sensitive fields in plugins before recording (current truncation logic does not affect sensitive content).

---

## 6. H2 File Database

The default file-based H2 database stores data in the user's home directory:

```text
${user.home}/.damning-proxy/data.mv.db
```

### Recommendations

- Switch to PostgreSQL/MySQL in production.
- Stop the service before backing up H2 data files to avoid file corruption.

---

## Security Improvement Roadmap (Recommended)

| Priority | Improvement |
|---|---|
| High | Add authentication for `/api/*` |
| High | Restrict CORS origins |
| Medium | Sandbox plugin execution |
| Medium | Encrypt sensitive configurations or use environment variables |
| Low | Auto-desensitize sensitive fields in logs |
