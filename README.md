# [Spring AI](https://docs.spring.io/spring-ai/reference/)

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

[https://www.promptingguide.ai/](https://www.promptingguide.ai/)
