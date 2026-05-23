# todo

## transcription (speech-to-text)

Spring AI 2.0.0-M7 only ships `OpenAiAudioTranscriptionModel` and `AzureOpenAiAudioTranscriptionModel` — both require
paid cloud access. No free/self-hostable `TranscriptionModel` exists in the framework yet (Ollama starter covers chat +
embeddings only).

**Recommended free approach: faster-whisper-server (drop-in)**

[faster-whisper-server](https://github.com/fedirz/faster-whisper-server) exposes an OpenAI-compatible
`/v1/audio/transcriptions` endpoint — no custom code needed, just point the existing `OpenAiAudioTranscriptionModel` at
it:

```yaml
spring:
  ai:
    openai:
      base-url: http://localhost:8000
      api-key: ignored
      audio:
        transcription:
          model: Systran/faster-whisper-small
```

Add to `compose.yaml`:

```yaml
faster-whisper:
  image: fedirz/faster-whisper-server:latest-cpu # or latest-cuda
  ports:
    - "8000:8000"
  environment:
    WHISPER_MODEL: Systran/faster-whisper-small
```

**Alternative: custom `TranscriptionModel` over whisper.cpp**

If faster-whisper-server doesn't fit, implement a thin `TranscriptionModel` wrapper that POSTs multipart audio to a
`whisper.cpp` HTTP server.

---

## custom MCP server (learning exercise)

Extract `getTimeAt` and `listFiles` from `tool/ToolService.kt` into a standalone Spring Boot app. Implement both transports to learn each approach.

**How stdio transport works**

The MCP client spawns the server as a subprocess and talks to it via JSON-RPC 2.0 over stdin/stdout pipes:

```
MCP Client (this app)               stdio server (subprocess)
       |                                      |
       |──stdin──► {"jsonrpc":"2.0","method":"tools/call","params":{"name":"getTimeAt",...}}
       |                                      |
       |                           @Tool method runs normally,
       |                           framework serializes return value
       |                                      |
       |◄─stdout── {"jsonrpc":"2.0","result":{"content":[{"type":"text","text":"..."}]}}
```

`@Tool` methods just return values normally — the stdio starter owns the stdin/stdout loop entirely. This is why logs must go to stderr: any stdout output that isn't valid JSON-RPC corrupts the protocol and crashes the client.

With supergateway, it's the same flow with an HTTP layer in front — it proxies HTTP→stdin and stdout→SSE. The server doesn't know there's a network layer:

```
MCP Client      supergateway         stdio server
    |────SSE────►|────stdin──────────►|
    |◄───SSE─────|◄───stdout──────────|
```

**Shared wiring** — `Tools` stays as-is with its `@Tool` annotations, just register it as a provider:

```kotlin
@Bean
fun toolCallbackProvider(tools: Tools): ToolCallbackProvider =
    MethodToolCallbackProvider.builder().toolObjects(tools).build()
```

```yaml
spring:
  ai:
    mcp:
      server:
        name: my-tools
        version: 1.0.0
```

---

### Option A — SSE (`spring-ai-starter-mcp-server-webflux`)

Server exposes `/sse` over HTTP directly — no supergateway, no log concerns.

```kotlin
implementation("org.springframework.ai:spring-ai-starter-mcp-server-webflux")
```

Connect from this app (container profile):

```yaml
spring:
  ai:
    mcp:
      client:
        sse:
          connections:
            my-tools:
              url: http://mcp-tools:3001
```

Add to `compose.yaml`:

```yaml
mcp-tools:
  image: my-tools-mcp-server:latest
  ports:
    - "3001"
  restart: unless-stopped
```

---

### Option B — stdio via supergateway (`spring-ai-starter-mcp-server-stdio`)

Server reads/writes MCP protocol on stdin/stdout — **logs must go to stderr** or they corrupt the protocol:

```kotlin
implementation("org.springframework.ai:spring-ai-starter-mcp-server-stdio")
```

```xml
<!-- logback-spring.xml -->
<configuration>
  <appender name="STDERR" class="ch.qos.logback.core.ConsoleAppender">
    <target>System.err</target>
    <encoder><pattern
      >%d{HH:mm:ss} %-5level %logger{36} - %msg%n</pattern></encoder>
  </appender>
  <root level="WARN"><appender-ref ref="WARN" /></root>
</configuration>
```

```yaml
spring:
  main:
    web-application-type: none
```

Wrap with supergateway in `compose.yaml` to expose it over SSE (same trick as `mcp-filesystem`):

```yaml
mcp-tools:
  image: node:lts-alpine
  command: >
    sh -c "npx --yes supergateway
           --stdio 'java -jar /app/my-tools-mcp-server.jar'
           --port 3001"
  volumes:
    - /path/to/my-tools-mcp-server.jar:/app/my-tools-mcp-server.jar
  ports:
    - "3001"
  restart: unless-stopped
```

Client config is identical to option A — supergateway bridges the transport, the client just sees SSE.

---

## file upload: replace multipart with presigned URL

Current `POST /api/file` receives the file through the app (multipart), then streams it to MinIO via
`S3AsyncClient`. This works but routes all bytes through the JVM — wasteful for large files even with streaming.

**Better approach: presigned URL**

1. Client calls a new endpoint, e.g. `GET /api/file/upload-url?filename=report.pdf`
2. App generates a time-limited S3 presigned PUT URL via `S3Presigner` (same AWS SDK, no extra dependency)
3. App returns `{ "url": "...", "expiresIn": 300 }` — client uploads directly to MinIO with a plain HTTP PUT
4. Pipeline picks the file up on the next poll cycle, same as today

```
client ──GET /api/file/upload-url──► app ──S3Presigner──► MinIO
client ◄── { url, expiresIn } ──────────────────────────────────
client ──PUT <presigned url> ────────────────────────────────────────────► MinIO
```

**Why it's better**

- File bytes never touch the app — no JVM memory pressure, no `max-request-size` limit
- Upload speed: one fewer network hop (client → MinIO directly instead of client → app → MinIO)
- The current `multipart.max-file-size` / `max-request-size` config in `application.yaml` becomes irrelevant

**Implementation notes**

- Use `S3Presigner` (from `software.amazon.awssdk:s3`): `presignPutObject(PutObjectPresignRequest)`
- `S3Presigner` needs the same endpoint override as `S3AsyncClient` (MinIO path-style, `http://localhost:9000`)
  — wire it as a `@Bean` alongside the existing S3 beans in `Configs.kt`
- Remove the current `POST /api/file` multipart endpoint once the presigned URL flow is in place
- ~~The `filename-regex` filter in `customS3Supplier` still applies — only matching filenames get ingested~~
- **only `filename-regex` should pass from the start**
- **Internal hostname problem:** `S3Presigner` generates URLs using the endpoint it was configured with
  (`http://minio:9000` in the `container` profile). A browser or external client can't reach that internal hostname.
  Two fixes:
  - Set `MINIO_SITE_URL` env var on the MinIO container to the public-facing URL — MinIO uses it when generating URLs
  - Or rewrite the host in the app before returning the presigned URL to the client (replace the internal host with
    the public host, keeping the path and signature query params intact — the signature is not over the hostname so
    it stays valid)
