# Pipeline design choices, v1

Specs for [Functions](../src/main/kotlin/com/hamza/springai/rag/Functions.kt).

**CLAUDE: always check for inconsistencies and spec drift, never modify this file inform me in STDOUT when u need to
modify something.**

## overview

The purpose of this pipeline is to ingest specific files from an input location and embed them into a vector store.

The files are filtered according to regexp on extension by env var `${INGEST_FILES_FILTER}`.

The input location is already configured in infra(docker, compose) and in code(spring boot, s3 integration) as MinIO
object store, default bucket is specified by `${MINIO_DEFAULT_BUCKET}`.

The vector store and embedding models is out of spec, it is handled transparently elsewhere, and shouldn't influence
design.

## tech choices

I need the ingestion pipeline to be designed using cloud functions so we can plug and play functionalities as this
evolves.

From my initial tests SCF, don't work well with Java's `Optional` and Kotlin's `?`, so we should probably use reactor
stack (SCF support empty).

## perf constraints

- **Performance, low mem usage and ability to ingest large files without OOMing or blocking resources are the top
  objectives**.
- Never, in any condition, load a whole file in memory while doing any operation.

## functional constraints

- pipeline use simple polling in v1.
- we should track if pipe is running to prevent another trigger (atomic bool).
- we should track how long its running (atomic long) to detect hanging and force stop.
- just pass `file_name` in all stages for logging.
- if there's any error anywhere in pipeline, move current file to `/error/` for manual correction.

### worth nothing

- If it's possible to avoid webflux all together without a gas factory around Optionals, it's even better. If not we
  can
  go webflux route.
- Project is using JDK25+ and virtual threads are enabled.

## functions

```text
S3_Supplier --> ${MINIO_DEFAULT_BUCKET}/
    |
    |
    |--> Duplication_Filter --> Document_Reader --> Document_Splitter --> Vector_Store_Writer --> S3_Archiver
```

### S3_Supplier

Entry and only function that can poll object store to trigger pipeline, I don't want any other function to poll at
will (
to avoid races)

it should:

1. list available files from `${MINIO_DEFAULT_BUCKET}` root
2. filter on `${INGEST_FILES_FILTER}` regexp
3. filter empty files
4. then forward `file_name` and `file_hash` (retrieved from S3, do not compute hash) to `Duplication_Filter` (so it
   doesn't
   have to call s3)

### Duplication_Filter

Receives `file_name` and `file_hash` from `S3_Supplier` then
uses [FileService](../src/main/kotlin/com/hamza/springai/rag/file/FileService.kt) (don't focus on db services, it's out
of scop here) to check if a DB entry with same content hash exists:

- exits: move file from `/` to `/processed/`, then trigger `Document_Reader` with empty message
- not: move file from `/` to `/processing/`, then trigger `Document_Reader` with `/processing/file_name`

### Document_Reader

Apache Tika, receives `/processing/file_name` from `Duplication_Filter`, then should stream file from S3 (remember do
not load a
whole file in memory). Tika can produce multiple text documents from a single page.

For v1 lets do basic cardinality : 1 file in input → all tika text documents in output.

Should forward list of text documents it produces to `Document_Splitter`.

### Document_Splitter

Receives list of tika documents from `Document_Reader` then uses
`org.springframework.ai.transformer.splitter.TokenTextSplitter` to tokenize them.

again: 1 file = 1 emission, do not explode

Should forward list of token it produces to `Vector_Store_Writer`

### Vector_Store_Writer

- Receives list of tokens from `Document_Splitter`
- write them in vector store
- write file hash in DB :
  - if there's a hash unique constraint error (race condition) move file to `/error/`
- forward `file_name` to `S3_Archiver`

Vector and DB write are not atomic in V1

### S3_Archiver

Exit, receives `file_name` from `Vector_Store_Writer` and move file to `/processed/`, then mark pipeline as
finished.
