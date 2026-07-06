[中文版](01-quickstart.md)

# 01 Quickstart

> Last updated: 2026-06-17  
> Source version: current workspace

## Get It Running in 5 Minutes

### 1. Check the Environment

```bash
java -version  # JDK 21 required
mvn -version   # Maven 3.9+ required
```

### 2. Start Dev Mode

```bash
mvn quarkus:dev
```

The first startup downloads Node/npm and builds the frontend; this takes about 1–3 minutes.

### 3. Open the Admin UI

Open your browser at:

```text
http://localhost:12360/
```

It will redirect automatically to `/admin/index.html`.

---

## Configure Your First Upstream Profile

1. Go to the **Upstream Profiles** page.
2. Click **New** and fill in:
   - Name: `OpenAI`
   - Slug: `openai`
   - Base URL: `https://api.openai.com/v1`
   - Bearer Token: your OpenAI API Key
   - Default model: `gpt-4o`
   - Timeout: 30000
3. Save.

---

## Create Your First Instance

1. Go to the **Instance Management** page.
2. Click **New** and fill in:
   - Name: `My Instance`
   - Slug: `my-instance`
   - Upstream profile: select the `openai` profile you just created
   - Plugin group: select `sample-js` or `sample-groovy` (created automatically on startup)
3. Save.

After creation, the page shows a copyable OpenAI URL:

```text
http://localhost:12360/v1/proxy/my-instance
```

---

## Test with curl

### Model list

```bash
curl http://localhost:12360/v1/proxy/my-instance/models
```

### Chat completion

```bash
curl -H "Authorization: Bearer sk-test" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4o",
    "messages": [{"role": "user", "content": "Hello"}]
  }' \
  http://localhost:12360/v1/proxy/my-instance/chat/completions
```

### Streaming

```bash
curl -N -H "Authorization: Bearer sk-test" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "model": "gpt-4o",
    "messages": [{"role": "user", "content": "Hello"}],
    "stream": true
  }' \
  http://localhost:12360/v1/proxy/my-instance/chat/completions
```

---

## View Traffic Logs

After each request, go to the **Traffic Logs** page to:

- View request/response payloads.
- View the plugin execution pipeline.
- View plugin logs.
- Delete or clear logs.

---

## Next Steps

- Learn plugin development: [02 Plugin Development Guide](02-plugin-development.en.md)
- Connect OpenCode: [03 Connect OpenCode](03-connect-opencode.en.md)
- View API docs: [04 API Docs](../04-api/)
