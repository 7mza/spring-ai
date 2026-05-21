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
- [enrichers/transformers](src/main/kotlin/com/hamza/springai/rag/pipeline/Transformers.kt)
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

for Nvidia
gpu [Nvidia container toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html)

if not, disable `services.ollama.deploy` section in [compose.yaml](compose.yaml)

## conf

[.env](.env)

Choose an [LLM](https://ollama.com/search) that will fit your hardware, `gemma4:e4b` is configured by default

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
./gradlew clean ktlintFormat ktlintCheck build -x test --no-build-cache && ./gradlew --stop
```

## run

Spring is configured with compose support, run with Gradle

```shell
./gradlew bootRun
```

or with compose

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

Download a wiki article as MD:

```shell
# apt install pandoc
curl -s "https://en.wikipedia.org/w/index.php?action=raw&title=Paris" | pandoc -f mediawiki -t markdown -o paris.md
```

## todo

- await tests are unreliable because ollama/gpu timeouts
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
