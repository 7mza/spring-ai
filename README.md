# [Spring AI](https://docs.spring.io/spring-ai/reference/)

## desc

Spring AI experimentation workspace with some design choices

- Ollama as LLM backend
- qdrant as vector store
- embedded h2 as db

## demo

- basic + template prompting
- LLM object response parsing with retry/recover
- LLM eval
- continuous file ingestion pipeline using spring cloud functions

```text
# ${INGEST_DIR} will be mounted as /home/${INGEST_DIR} in docker

${INGEST_DIR}/
    |
    |_> fileSupplier -> duplicationFilter -> documentReader -> documentSplitter -> vectorStoreWriter
```

- RAG
-

## requirements

[SDKMAN](https://sdkman.io/)

[nvm](https://github.com/nvm-sh/nvm)

[docker](https://docs.docker.com/engine/install/)

[Nvidia container toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html) (only if Nvidia gpu, if not drop services.ollama.deploy block in [compose.yaml](compose.yaml))

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

[qdrant ui](http://localhost:6333/dashboard#/collections)

delete qdrant collection:

```shell
curl -X DELETE "http://localhost:6333/collections/embeddings"
```

[h2](http://localhost:8080/h2) `# jdbc url = jdbc:h2:mem:test_db (from .env)`
