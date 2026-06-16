# daming-proxy

A high-performance, OpenAI-compatible API server built with **Java 21**, **Quarkus**, and **GraalVM Native Image** support.

## Features

- **OpenAI-compatible Chat Completions API** (`POST /v1/chat/completions`) with SSE streaming
- **Models API** (`GET /v1/models`) for model discovery
- **Bearer Token Authentication**
- **GraalVM Native Image** ready — compiles to a fast-starting, low-memory native binary
- **Mock LLM Service** included for testing and development

## Tech Stack

| Technology | Version |
|-----------|---------|
| Java | 21 |
| Quarkus | 3.15.1 |
| Maven | 3.9+ |
| GraalVM | 23.1+ (for native compilation) |

## Quick Start

### 1. Run in Development Mode

```bash
mvn quarkus:dev
```

The server starts on `http://localhost:12360`.

### 2. Build and Run JAR

```bash
mvn clean package
java -jar target/daming-proxy-1.0-SNAPSHOT-runner.jar
```

### 3. Build Native Image (requires GraalVM)

```bash
mvn clean package -Pnative
./target/daming-proxy-1.0-SNAPSHOT-runner
```

## API Endpoints

| Method | Path | Description | Auth Required |
|--------|------|-------------|---------------|
| `POST` | `/v1/chat/completions` | Streaming chat completions | Yes |
| `GET` | `/v1/models` | List available models | Yes |
| `GET` | `/v1/health` | Health check | No |

### Authentication

All protected endpoints require a Bearer token:

```
Authorization: Bearer sk-daming-proxy-demo-token
```

The token can be customized via the `API_TOKEN` environment variable.

### Test with cURL

**List models:**
```bash
curl -H "Authorization: Bearer sk-daming-proxy-demo-token" \
  http://localhost:12360/v1/models
```

**Chat completions (streaming):**
```bash
curl -N -H "Authorization: Bearer sk-daming-proxy-demo-token" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "mock-gpt-4o",
    "messages": [{"role": "user", "content": "Hello!"}],
    "stream": true
  }' \
  http://localhost:12360/v1/chat/completions
```

## Connect with OpenCode

To use this server as a custom provider in [OpenCode](https://opencode.ai), add the following configuration to your `opencode.json`:

```json
{
  "$schema": "https://opencode.ai/config.json",
  "provider": {
    "daming-proxy": {
      "npm": "@ai-sdk/openai-compatible",
      "name": "Daming Proxy (local)",
      "options": {
        "baseURL": "http://localhost:12360/v1",
        "apiKey": "sk-daming-proxy-demo-token"
      },
      "models": {
        "mock-gpt-4o": {
          "name": "Mock GPT-4o"
        },
        "mock-claude-3-sonnet": {
          "name": "Mock Claude 3 Sonnet"
        },
        "mock-llama-3-70b": {
          "name": "Mock Llama 3 70B"
        }
      }
    }
  }
}
```

### Configuration Explained

| Field | Description |
|-------|-------------|
| `npm` | The AI SDK package to use. `@ai-sdk/openai-compatible` for OpenAI-compatible providers |
| `name` | Display name for the provider in the OpenCode UI |
| `options.baseURL` | Your server URL + `/v1` path |
| `options.apiKey` | Must match the `API_TOKEN` environment variable (default: `sk-daming-proxy-demo-token`) |
| `models` | Maps model IDs to their display names. The IDs must match those returned by `GET /v1/models` |

### Model IDs

The server exposes these mock models via `/v1/models`:

- `mock-gpt-4o`
- `mock-claude-3-sonnet`
- `mock-llama-3-70b`

### Switching Models in OpenCode

After configuring, you can switch to your custom provider in OpenCode:

1. Open the model selector in OpenCode
2. Choose `daming-proxy` as the provider
3. Select one of the configured models (`mock-gpt-4o`, etc.)

## Project Structure

```
src/
├── main/
│   ├── java/com/xenoamess/daming_proxy/
│   │   ├── Main.java                    # Application entry point
│   │   ├── api/
│   │   │   └── OpenAiCompatibleApi.java # REST API endpoints
│   │   ├── dto/                         # Request/response DTOs
│   │   │   ├── ChatCompletionRequest.java
│   │   │   ├── ChatCompletionChunk.java
│   │   │   ├── ChatMessage.java
│   │   │   ├── ModelListResponse.java
│   │   │   └── ...
│   │   ├── filter/
│   │   │   ├── BearerTokenFilter.java   # Auth filter
│   │   │   └── GlobalExceptionMapper.java
│   │   └── service/
│   │       └── MockLlmService.java      # Mock LLM streaming logic
│   └── resources/
│       └── application.properties       # Quarkus config
└── test/
    └── java/.../OpenAiCompatibleApiTest.java
```

## Configuration

### Application Properties (`application.properties`)

```properties
quarkus.http.port=12360

# CORS (enabled for OpenCode client)
quarkus.http.cors=true
quarkus.http.cors.origins=*

# Logging
quarkus.log.level=INFO
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `API_TOKEN` | `sk-daming-proxy-demo-token` | Bearer token for API authentication |

## Development

### Run Tests

```bash
mvn test
```

### Dev Mode with Hot Reload

```bash
mvn quarkus:dev
```

Press `r` in the terminal to run tests, `h` for help.

## License

MIT
