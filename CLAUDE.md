# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Stack

- **Language**: Kotlin / Spring Boot 4.0.6 / Java 25
- **AI**: Spring AI 2.0.0-M6 with Ollama (chat + embeddings)
- **Vector store**: Qdrant
- **Object store**: MinIO (S3-compatible)
- **Database**: H2, local file
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
./gradlew test --tests "com.hamza.springai.rag.pipeline.FunctionsIntegrationTest"
```

Integration tests spin up real Testcontainers (MinIO, Qdrant, Ollama with GPU passthrough).

### Lint / Format

```shell
./gradlew ktlintFormat   # auto-fix Kotlin style
./gradlew ktlintCheck    # check only
npm run format           # prettier for YAML/JSON/XML/MD
```

### Misc

```shell
./update.sh                          # npm-check-updates + gradlew dependencyUpdates (combined upgrade check)
./gradlew generateOpenApiDocs        # generate docs/api-docs.yaml (starts app on port 8013)
./gradlew dependencyCheckAnalyze     # OWASP CVE scan (needs NVD_APIKEY env var)
./clean.sh                           # hard clean: removes .gradle/, node_modules/, package-lock.json
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
A `shared/` package provides `PageMeta` and `SortField` for paginated responses used across all feature areas.

### File API (`rag/file/`)

`POST /api/file` (multipart) — uploads a file directly to the MinIO bucket root; the pipeline picks it up on its next
poll. `GET /api/file` — paginated list of already-ingested files (name, hash, createdAt). File upload requires S3 to
be enabled (`spring.cloud.aws.s3.enabled=true`); `FileService` uses `ObjectProvider<S3AsyncClient>` and will throw at
runtime if called with S3 disabled.

### Data layer (`data/`)

All JPA entities extend `BaseEntity<ID>` which provides `createdAt`, `updatedAt`, and `version` (optimistic locking).
Entity PKs are TSID-based: stored as `Long` in H2, exposed as base62 strings in the API via `TSID.encode(62)`. The
`TSIDAttributeConverter` applies automatically via `@Converter(autoApply = true)`.

Each entity requires a companion `@Embeddable` ID class (e.g. `FileId`) implementing the `EntityId` interface, with a
no-arg constructor for JPA, a string constructor that decodes base62, and a `toString()` that encodes to base62. Use
`@EmbeddedId` on the entity field, not `@Id`.

### Caching

The `File` entity uses Hibernate L2 cache (JCache/Ehcache, region `"files"`, configured in `ehcache.xml`). The L2
cache is **disabled by default in tests** (`spring.cache.disabled=true`). Enable per-test with:

```kotlin
SpringBootTest(properties = ["spring.jpa.properties.hibernate.cache.use_second_level_cache=true"])
class Test
```

### Ingestion pipeline (`rag/pipeline/Functions.kt`, spec: `docs/pipeline_specs_v1.md`)

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
`QuestionAnswerAdvisor`). Similarity threshold is 0.3; a debug log prints all scores without threshold for tuning. The
advisor mode supports Qdrant metadata filtering (e.g. `language == 'en'`) — useful once enrichers are wired.

### Prompt templates

All LLM prompt templates are `.st` (StringTemplate4) files under `src/main/resources/prompt_templates/`:

- `prompt/` — topic template for structured response examples
- `rag/` — language detection and quality evaluation prompts for enrichers
- `eval/` — system + evaluation prompts for `ScoreEvaluator`

### Spring Retry (structured output)

`@Retryable(JacksonException, maxAttempts=5)` + `@Recover` fallback is the pattern for LLM endpoints that must return
structured JSON. Used in `PromptService.songs()` and `ScoreEvaluator.evaluate()`. The Ollama client timeout is
overridden to 2 minutes in `Configs.kt` to accommodate slow model responses.

### Config profiles

| Profile     | Used when                                                                              |
| ----------- | -------------------------------------------------------------------------------------- |
| `default`   | Local dev (active by default)                                                          |
| `container` | Docker Compose deployment — disables docker compose support, changes service hostnames |
| `openapi`   | Doc generation only — silences all logs, uses port 8013                                |

`QdrantConfigs` (in `Configs.kt`) mirrors JPA `create-drop`: it wipes the Qdrant collection on shutdown when
`spring.jpa.hibernate.ddl-auto=create-drop`. This is active in the `default` profile for clean dev restarts.

**Compose files**: `compose.yaml` is the full service definition. `compose.spring.yaml` (used by Spring Boot's Docker
Compose support in dev mode) re-exports the same services behind profiles (`minio`, `ollama`, `qdrant`) for selective
startup. The `container` profile points to the hostnames defined in `compose.yaml`.

### Environment variables

Key variables consumed by `application.yaml` (set in `.env` for local dev):

| Variable                                             | Purpose                                          |
| ---------------------------------------------------- | ------------------------------------------------ |
| `INGEST_FILES_FILTER`                                | Regex to filter which S3 keys enter the pipeline |
| `INGEST_POLL_INTERVAL`                               | Polling interval in ms for the S3 supplier       |
| `MINIO_DEFAULT_BUCKET`                               | MinIO bucket name (`custom.supplier.remote-dir`) |
| `OLLAMA_MODEL`                                       | Chat model name (e.g. `llama3.2`)                |
| `OLLAMA_EMBEDDING_MODEL`                             | Embedding model name (e.g. `nomic-embed-text`)   |
| `OLLAMA_TEMPERATURE`, `OLLAMA_TOP_K`, `OLLAMA_TOP_P` | LLM sampling params                              |
| `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`            | MinIO credentials                                |

### Atomicity / known limitations

- H2 and Qdrant writes are **not atomic** — a crash between them leaves orphaned vectors
- Files stranded in `processing/` after a crash require manual recovery (move back to root)
- Outbox pattern (H2 as source of truth + reliable event log) is planned to address both

## Testing conventions

- Integration tests import **only necessary** containers through `TestcontainersConfig` file and optionally
  `PipelineHelperService` to init bucket.
- WireMock (`wiremock-spring-boot`) for HTTP-level LLM mocking in unit tests when possible.
- S3 autoconfiguration (for pipeline) is optional through `spring.cloud.aws.s3.enabled` and disabled by default in tests
  conf. Enable it explicitly when a test needs it, example `FunctionsIntegrationTest`.

## Dev consoles

- Swagger UI: http://localhost:8080/swagger-ui
- H2 console: http://localhost:8080/h2 (JDBC URL: `jdbc:h2:file:~/springai_db` from .env)
- MinIO console: http://localhost:9001/browser/default/
- Qdrant dashboard: http://localhost:6333/dashboard#/collections
