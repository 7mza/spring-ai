# [Spring AI](https://docs.spring.io/spring-ai/reference/)

## desc

Spring AI experimentation workspace with some design choices

- Ollama as LLM backend
- Qdrant as vector store
- MinIO as object store
- embedded H2 as DB

## demo

- basic + template prompting
- LLM object response parsing with retry/recover
- LLM eval
- continuous file ingestion pipeline using spring cloud functions
- [enrichers/transformers](src/main/kotlin/com/hamza/springai/rag/pipeline/Functions.kt)
  - language detection
  - quality evaluation
  - keywords
  - summary
- RAG
  - manual
  - advisors
- ...

### [ingestion pipeline](src/main/kotlin/com/hamza/springai/rag/pipeline/Functions.kt)

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
in [application.yml](src/main/resources/application.yaml) between documentSplitter and vectorStoreWriter (they will slow
down pipeline)

## requirements

[SDKMAN](https://sdkman.io/)

[nvm](https://github.com/nvm-sh/nvm)

[docker](https://docs.docker.com/engine/install/)

[Nvidia container toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html)
(only if Nvidia gpu, if not drop services.ollama.deploy block in [compose.yaml](compose.yaml))

## conf

[.env](.env)

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
./gradlew clean ktlintFormat ktlintCheck build
```

## run

Spring is configured with compose support, run with ide or

```shell
./gradlew jibDockerBuild -x test --no-build-cache && ./gradlew --stop
```

```shell
docker compose up --build
```

[http://localhost:8080/swagger-ui](http://localhost:8080/swagger-ui)

## misc

[MinIO console](http://localhost:9001/browser/default/)

[Qdrant console](http://localhost:6333/dashboard#/collections)

delete qdrant collection:

```shell
curl -X DELETE "http://localhost:6333/collections/embeddings"
```

[H2 console](http://localhost:8080/h2) `# jdbc url = jdbc:h2:file:~/springai_db (from .env)`

## todo

- `app --poll--> minio` to `app <--notify-- minio` (SQS/SNS)
- atomic H2 and Qdrant write: outbox (H2 as source of truth)

## clean

```shell
# remove local h2 db
rm ~/springai_db.mv.db
```

```shell
# clean docker volumes
docker volume rm \
  minio_data \
  ollama_data \
  qdrant_data \
  springai_data
```
