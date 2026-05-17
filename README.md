# [Spring AI](https://docs.spring.io/spring-ai/reference/)

## desc

Spring AI workspace with some personal design choices

- Ollama as LLM backend
- qdrant as vector store
- embedded h2 as db

## demo

- basic + template prompting
- LLM response parsing with retry/recover
- LLM eval
- continuous file ingestion pipeline using spring cloud functions
- RAG
-

## requirements

[sdkman](https://sdkman.io/)

[nvm](https://github.com/nvm-sh/nvm)

[docker](https://docs.docker.com/engine/install/)

[nvidia container toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html)

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

[h2](http://localhost:8080/h2)
