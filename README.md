# [Spring AI](https://spring.io/projects/spring-ai#learn)

## desc

Spring AI experimentation workspace

- Ollama as LLM backend
- Speaches as TTS/STT backend
- Qdrant as vector store
- MinIO as object store

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

## requirements

[SDKMAN](https://sdkman.io/)

[nvm](https://github.com/nvm-sh/nvm)

[docker](https://docs.docker.com/engine/install/)

for Nvidia
gpu [Nvidia container toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html)

## conf

[.env](.env)

**Choose an [LLM](https://ollama.com/search) that fit your hardware, especially if running in CPU mode**

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

```shell
# CPU mode (ollama/speaches)
docker compose up --build
```

```shell
# GPU mode (require docker CDI)
docker compose -f compose.yaml -f compose.gpu.yaml up --build
```

[http://localhost:8080/swagger-ui](http://localhost:8080/swagger-ui)

## misc

- [Grafana](http://localhost:9091/) / [Prometheus](http://localhost:9090/) / [Jaeger](http://localhost:16686/)
- [Speaches](http://localhost:8000)
- [MinIO](http://localhost:9001/browser/default/)
- [Qdrant](http://localhost:6333/dashboard#/collections)
- [H2](http://localhost:8080/h2) `# jdbc url = jdbc:h2:file:~/springai_db (from .env)`
