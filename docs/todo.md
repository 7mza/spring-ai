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
