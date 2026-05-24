# CLAUDE.md

## desc

this project is a `Spring AI` learning and experimentation workspace

## hard rules

never read `docs/examples/*` it is not representative of the codebase

## code base discovery

no need to read every file on this project when you are building a new context I will always be guiding you

when you need details on stack / dependencies it can be inferred though [gradlew build](build.gradle.kts)

app configurations can be inferred through [config class](src/main/kotlin/com/hamza/springai/Configs.kt)
and [main YAML](src/main/resources/application.yaml) / [test YAML](src/test/resources/application.yaml)

infra can be inferred through [docker compose](compose.yaml)

env vars can be inferred through [.env](.env)

don't modify this file on `/init` if you deem something is important and worth adding inform me in STDOUT

if you can infer it through walking code base = it's not important to add to `claude.md`

if you can only infer it through current context or a memory = it's a good candidate inform me

## fresh eyes

when I ask for **fresh eyes** spawn a subagent

**never context poison fresh eyes it's job is to be critical not a confirmation bias agent for previous decisions**

pass it only a short and condensed description of **current problem or question, solution or envisioned solution and the
why and why not** we would have discussed earlier

before passing down new context show it to me in STDIO so I can validate

## ref files

when I ask you to note something for future reference do it in [notes](docs/notes.md)

when I ask you to note something as a todo do it in [todo](docs/todo.md)

## specs

when working on [file ingestion pipeline](src/main/kotlin/com/hamza/springai/rag/pipeline/Functions.kt) always
check its [specs](docs/pipeline_specs_v1.md)

## code style

always prefer Kotlin sugar (expression bodies, `let`, `also`, `apply`, destructuring, `when`, extension functions,
trailing lambdas, ...etc.) over Java verbosity, inform me if something slips
