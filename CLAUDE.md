# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Stack

- **Language**: Kotlin / Spring Boot 4.0.6 / Java 25
- **AI**: Spring AI 2.0.0-M6 with Ollama (chat + embeddings)
- **Vector store**: Qdrant
- **Object store**: MinIO (S3-compatible)
- **Database**: H2 in-memory (resets on restart; outbox pattern is planned for persistence)
- **Pipeline**: Spring Cloud Function

## Commands

### Setup (once)

```shell
nvm use          # set Node version (.nvmrc)
npm i            # install prettier + plugins
sdk env install  # set Java/Kotlin version (.sdkmanrc)
```

### Build

```shell
./gradlew clean ktlintFormat ktlintCheck build
```

### Run (dev)

Spring Boot's Docker Compose support auto-starts MinIO, Ollama, and Qdrant. Run via IDE or:

```shell
./gradlew jibDockerBuild -x test --no-build-cache && ./gradlew --stop
docker compose up --build
```

### Test

```shell
./gradlew test
./gradlew test --tests "com.hamza.springai.rag.FunctionsIntegrationTest"
```

Integration tests spin up real Testcontainers (MinIO, Qdrant, Ollama with GPU passthrough) — they are slow.

### Lint / Format

```shell
./gradlew ktlintFormat   # auto-fix Kotlin style
./gradlew ktlintCheck    # check only
npm run format           # prettier for YAML/JSON/XML/MD
```

### Misc

```shell
./gradlew dependencyUpdates          # check for newer versions
./gradlew generateOpenApiDocs        # generate docs/api-docs.yaml (starts app on port 8013)
./gradlew dependencyCheckAnalyze     # OWASP CVE scan (needs NVD_APIKEY env var)
```

## Architecture

### Feature areas

Three packages under `com.hamza.springai`, each following the same layering:

| Package       | Purpose                                                                                          |
| ------------- | ------------------------------------------------------------------------------------------------ |
| `prompt/`     | Basic + template prompting, structured JSON responses, streaming, Spring Retry on parse failures |
| `rag/`        | Ingestion pipeline, manual RAG, advisor-based RAG                                                |
| `evaluation/` | LLM-as-judge evaluation via Spring AI `Evaluator`                                                |

Each package has a `I*Api` interface (OpenAPI contract), `*Ctrl` (REST controller), `*Service`/`I*Service`, and `*Dtos`.

### Ingestion pipeline (`rag/Functions.kt`, spec: `docs/pipeline_specs_v1.md`)

When working on the pipeline, always check `docs/pipeline_specs_v1.md` for drift. **Never modify that file directly** —
report inconsistencies to the user first.

Spring Cloud Function beans wired via `spring.cloud.function.definition` in `application.yaml`:

```
customS3Supplier → duplicationFilter → documentReader → documentSplitter → vectorStoreWriter → s3Archiver
```

**File lifecycle in MinIO**: `root/` → `processing/` → `processed/` (or `error/` on failure).

Key design decisions documented in the `Functions` class KDoc:

- `delimiter("/")` on S3 listing hides `processing/`/`processed/`/`error/` from polls
- `pipelineRunning: AtomicBoolean` prevents overlapping poll cycles; `pipelineWatchdog` force-resets it after
  `custom.supplier.pipeline-timeout` ms
- All blocking I/O (Tika, Qdrant, Ollama, H2) runs on a virtual-thread scheduler via
  `Schedulers.fromExecutorService(Executors.newVirtualThreadPerTaskExecutor())`
- Concurrency capped at 2 via `flatMap(..., 2)` at every stage to bound memory and LLM calls
- `S3AsyncClient` for all metadata ops; blocking `S3Client` only for `getObject` (Tika needs an `InputStream`)
- Files are streamed via `InputStreamResource` — no byte-array buffering

### Enrichers (not yet wired)

`languageEnricher`, `qualityEnricher`, `keywordEnricher`, `summaryEnricher` beans exist in `Functions.kt` but are **not
** in `spring.cloud.function.definition`. To enable one, add it between `documentSplitter` and `vectorStoreWriter` in
the definition string. Read the `qualityEnricher` KDoc before wiring `qualityEnricher` — there is a known silent-drop
risk that must be fixed first.

### RAG (`rag/RagService.kt`)

Two modes: manual (fetch context with `similaritySearch` then template-inject) and advisor-based (
`QuestionAnswerAdvisor`). Similarity threshold is 0.3; a debug log prints all scores without threshold for tuning.

### Config profiles

| Profile     | Used when                                                                              |
| ----------- | -------------------------------------------------------------------------------------- |
| `default`   | Local dev (active by default)                                                          |
| `container` | Docker Compose deployment — disables docker compose support, changes service hostnames |
| `openapi`   | Doc generation only — silences all logs, uses port 8013                                |

### Atomicity / known limitations

- H2 and Qdrant writes are **not atomic** — a crash between them leaves orphaned vectors
- H2 is in-memory — all deduplication state is lost on restart
- Files stranded in `processing/` after a crash require manual recovery (move back to root)
- Outbox pattern (H2 as source of truth + reliable event log) is planned to address both

## Testing conventions

- Integration tests import `TestcontainersConfig` and optionally `PipelineHelperService`
- `@TestInstance(Lifecycle.PER_CLASS)` + `@TestMethodOrder(OrderAnnotation::class)` used for ordered pipeline tests that
  share state
- WireMock (`wiremock-spring-boot`) for HTTP-level LLM mocking in unit tests

## Dev consoles

- Swagger UI: http://localhost:8080/swagger-ui
- H2 console: http://localhost:8080/h2 (JDBC URL: `jdbc:h2:mem:test_db`)
- MinIO console: http://localhost:9001/browser/default/
- Qdrant dashboard: http://localhost:6333/dashboard#/collections
