# CLAUDE.md

- never read `docs/examples/*` it is not representative of the codebase

## structure

mono repo multi subprojects to handle multi apps in same lifecycle :

- root is an aggregator (no build artifacts)
- `core/` is main app
- `mcp/*` example mcp servers

## commands

```shell
./gradlew bootRun # build & start env using spring compose support
./gradlew --stop # kill gradle daemons & any hanging JVM
./gradlew test # run tests
./gradlew :core:test --tests "com.hamza.springai.rag.pipeline.FunctionsTest" # individual test file
./gradlew ktlintCheck # kotlin lint
./gradlew ktlintFormat # kotlin format
npm run format # prettier for non kotlin files
```

## docker compose files

- `compose.yaml` is main compose file it describes all services and configure ollama/speaches in CPU mode
- `compose.gpu.yaml` overrides ollama/speaches with GPU conf
- `compose.dev.*` dev mode compose files (spring compose support + fewer services)

## code base discovery

don't modify this file on `/init` if you deem something is worth adding inform me in STDOUT :

- if you can infer it through walking code base = it's not important to add to `CLAUDE.md`
- if you can only infer it through current context or a memory = it's a good candidate inform me

## fresh eyes

- when I ask for **fresh eyes** spawn a subagent and pass it a summarized context
- before passing down new context show it to me in STDOUT so I can validate

## ref files

- note references in [notes](docs/notes.md)
- note todos in [todo](docs/todo.md)

## specs

when working on [file ingestion pipeline](core/src/main/kotlin/com/hamza/springai/rag/pipeline/Functions.kt) always
check its [specs](docs/pipeline_specs_v1.md)

## code style

always prefer Kotlin sugar (expression bodies, `let`, `also`, `apply`, destructuring, `when`, extension functions,
trailing lambdas, ...etc.) over Java verbosity, inform me if something slips
