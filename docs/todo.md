# todo

## grafana dashboard: Spring AI observability

Build a proper Spring AI Grafana dashboard from scratch.

**Two sections, two readers**

- Dev/debugging — "why is this slow / broken right now?"
- Cost/efficiency — "how much is this consuming over time?"

**Row 1 — at a glance (4 stats)**

- ~~Active requests right now (in-flight gauge, color threshold: 0=green, 2+=yellow, 5+=red)~~
- ~~Requests/min~~
- ~~Error rate % (alert if > 0)~~
- ~~Avg end-to-end latency (ChatClient level)~~

**Row 2 — token economics**

- Input tokens/min rate
- Output tokens/min rate
- Input/output ratio over time (climbs = RAG context bloat or history accumulation)
- Cumulative totals as stats (cost accounting)

**Row 3 — latency anatomy**

- Single time series with three overlaid lines: ChatClient p95 / raw LLM p95 / tool execution p95
- The gap between them is the diagnostic story — don't split into three separate panels
- Use a heatmap if histogram buckets are available

**Row 4 — tool behavior**

- Tool call rate per tool name
- Avg tool execution time per tool
- Tool error rate
- Tool iterations per request: `rate(advisor_count) / rate(chat_client_count)` — runaway loops show here

**Row 5 — errors**

- Error rate broken down by layer: LLM vs advisor vs tool (separate lines)
- Log panel if Loki is available

**Leave out of this dashboard**

- JVM metrics (link to a separate JVM dashboard)
- HikariCP / JDBC
- HTTP server / Tomcat metrics

**Key rule:** graph rates and gauges, not counters. Counters only as stat panels in row 2 for cost accounting.

**The layered latency architecture to keep in mind:**

```
spring.ai.chat.client        ← what the user feels
  └─ spring.ai.advisor       ← tool call loop
       └─ gen_ai.client.operation  ← raw model time
            └─ spring.ai.tool      ← function execution
```

---

## audio endpoint: replace MultipartFile with octet-stream for browser MediaRecorder

Current `POST /api/audio` uses `multipart/form-data` + `MultipartFile`. This works for form-based uploads (curl `-F`,
HTML `<input type="file">`), but is wrong for browser real-time audio capture.

**The browser use case**

The browser `MediaRecorder` API produces raw audio `Blob` chunks (e.g. `audio/webm;codecs=opus`). The natural way to
send these is a plain `fetch` with the blob as the body — no form, no multipart wrapper:

```js
fetch("/api/audio", {
  method: "POST",
  headers: { "Content-Type": "audio/webm" },
  body: audioBlob,
});
```

**What to change**

- Change `consumes` from `MULTIPART_FORM_DATA_VALUE` to the audio mime types the browser produces:
  `audio/webm`, `audio/ogg`, `audio/wav` (varies by browser/OS)
- Change the parameter from `@RequestPart("file") file: MultipartFile` to
  `@RequestBody body: ByteArray` (or `InputStream` for large files to avoid buffering)
- Convert to a Spring `ByteArrayResource` / `InputStreamResource` before passing to `OpenAiAudioTranscriptionModel`

**Keep multipart as a fallback?**

Only if there's a concrete need to support form uploads alongside browser streaming. Otherwise drop it — supporting
both content types adds negotiation complexity for no current benefit.

---

## image generation: local Flux.1-schnell via custom ImageModel

Spring AI's `ImageModel` abstraction has no built-in support for local inference servers.
Flux.1-schnell (quantized, ~6-8GB VRAM) running via ComfyUI or Automatic1111 is the recommended local model.

**What's needed**

- Run Flux.1-schnell via ComfyUI or Automatic1111 (both expose REST APIs)
- Implement a custom `ImageModel` bean that wraps the local server's API
  — `ImageModel` is a single-method interface (`call(ImagePrompt): ImageResponse`), low effort
- Wire it up as a Spring bean alongside the existing models
- Add a new endpoint (e.g. `POST /api/image/generate`) using the custom `ImageModel`

**Alternatives**

- SD 1.5 if VRAM is tight (~2GB) — lower quality
- Stability AI cloud API — Spring AI has a built-in implementation, no custom code needed

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
