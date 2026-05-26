# todo

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
