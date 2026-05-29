# [Spring AI](https://spring.io/projects/spring-ai#learn)

## desc

Spring AI learning/experimentation workspace

- Ollama as LLM backend
- Qdrant as vector store
- MinIO as object store
- embedded H2 as DB
- Speaches as TTS/STT backend

## demo

- basic/template + image prompting
- LLM object response parsing with retry/recover
- LLM eval
- continuous [file ingestion pipeline](core/src/main/kotlin/com/hamza/springai/rag/pipeline/Functions.kt) using spring
  cloud functions
- [enrichers/transformers](core/src/main/kotlin/com/hamza/springai/rag/pipeline/Transformers.kt)
  - language detection
  - quality evaluation
  - keywords
  - summary
- RAG
  - manual
  - advisors
  - query enhancing/expanding
- Persistent chat memory
  - JDBC backend
  - VectorStore backend
- Tools
- MCP
  - sync/STDIO + supergateway [server](mcp/mcp-weather) `inspector: npm run mcp + @localhost:3001/mcp`
  - async/streamableHttp [server](mcp/mcp-currency) `inspector: npm run mcp + @localhost:3002/mcp`
- local tts/stt
  - [speaches](https://github.com/speaches-ai/speaches/) posing as OpenAI audio API
  - [models configuration](docker/speaches/model_aliases.json)
- monitoring/observability
  - prom/[grafana](http://localhost:9091/dashboards)/jaeger (in mem, storage out of scope)
- ...

### [api](docs/api-docs.yaml)

### [ingestion pipeline](core/src/main/kotlin/com/hamza/springai/rag/pipeline/Functions.kt)

```text
                  1: poll
customS3Supplier ------> ${MINIO_DEFAULT_BUCKET}/
    |2: trigger                   |
    |           |-- duplicate --> |_ processed/ <----------------------------------------------------------|
    |           |                 |                                                                        |
    |           |------ new ----> |_ processing/ <------------------|                                      |
    |           |                 |                                 |                                      |
    |           |                 |- error/                         |                                      |
    |           |                                                   |                                      |
    |           |                   |-------------------------------|                                      |
    |           |                   |                                                                      |
    |           |4                  |5: pull                                                               |7: archive
    |-> duplicationFilter -> documentReader -> documentSplitter -> (*Enricher) -> vectorStoreWriter -> s3Archiver
                |3                                                       |                   |6: write
                |---- content hash check ----> DB <---|                  |----> LLM          |
                                                      |                                      |---> Qdrant
                                                      |                                      |
                                                      |--------- content hash write ---------|
```

any error in pipeline will move file to `/error` for manual correction

to test 1 or many enricher functions (unstable) add them in `spring.cloud.function.definition`
in [application.yaml](core/src/main/resources/application.yaml) between documentSplitter and vectorStoreWriter (they
will slow down pipeline)

## requirements

[SDKMAN](https://sdkman.io/)

[nvm](https://github.com/nvm-sh/nvm)

[docker](https://docs.docker.com/engine/install/)

for Nvidia
gpu [Nvidia container toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html)

## conf

[.env](.env)

**Choose an [LLM](https://ollama.com/search) that will fit your hardware, especially if running in CPU mode**

`gemma4:e4b` is configured by default

RAG quality depends on:

- **Input documents quality**
- temperature
- top_k
- top_p

Tweak these sampling parameters in [.env](.env)

## build

```shell
nvm use
```

```shell
npm i
```

```shell
sdk env install
```

```shell
./gradlew clean ktlintFormat ktlintCheck build jibDockerBuild -x test --no-build-cache && ./gradlew --stop
```

## run

run with docker compose

```shell
# CPU mode (ollama/speaches)
docker compose up --build
```

```shell
# GPU mode (require docker CDI)
docker compose -f compose.yaml -f compose.gpu.yaml up --build
```

or run with spring compose support

```shell
# CPU mode (ollama/speaches)
./gradlew bootRun
```

```shell
# GPU mode (require docker CDI)
SPRING_PROFILES_ACTIVE=default,gpu ./gradlew bootRun
```

[http://localhost:8080/swagger-ui](http://localhost:8080/swagger-ui)

## stop

```shell
docker compose stop && ./gradlew --stop
```

## misc

- [Grafana UI](http://localhost:9091/)
- [Jaeger UI](http://localhost:16686/)
- [Prometheus UI](http://localhost:9090/)
- [MinIO UI](http://localhost:9001/browser/default/)
- [Qdrant UI](http://localhost:6333/dashboard#/collections)
- [H2 UI](http://localhost:8080/h2) `# jdbc url = jdbc:h2:file:~/springai_db (from .env)`
- [Speaches UI](http://localhost:8000)

---

delete qdrant collection:

```shell
curl -X DELETE "http://localhost:6333/collections/embeddings"
```

Download a wiki article as MD (for RAG testing):

```shell
# apt install pandoc
curl -s "https://en.wikipedia.org/w/index.php?action=raw&title=Paris" | pandoc -f mediawiki -t markdown -o paris.md
```

---

## todo

- await tests are unreliable because ollama/gpu timeouts
- `app --poll--> minio` to `app <--notify-- minio` (SQS/SNS)
- atomic H2 and Qdrant write: outbox (H2 as source of truth)

## clean

remove local h2 db

```shell
rm ~/springai_db.mv.db
```

clean docker

```shell
docker volume prune -f && \
  docker network prune -f && \
  docker image prune -f && \
  docker builder prune -a -f && \
  docker buildx prune -a -f
```

```shell
docker volume rm \
  sa_minio_data \
  sa_ollama_data \
  sa_qdrant_data \
  sa_speaches_data \
  sa_springai_data
```

### owasp deps check

https://nvd.nist.gov/developers/request-an-api-key

```shell
mkdir -p ~/owasp-data && chmod 777 ~/owasp-data
```

```shell
docker run --rm \
  -v ~/owasp-data:/usr/share/dependency-check/data \
  owasp/dependency-check:latest \
  --updateonly \
  --nvdApiKey "$NVD_APIKEY"
```

```shell
./gradlew dependencyCheckAnalyze && ./gradlew --stop
```
