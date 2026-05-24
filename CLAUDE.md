this project is a Spring AI learning and experimentation workspace

- never read `docs/examples/*` it is not representative of the codebase
- no need to read every file on this project when you are building a new context I will always be guiding you but
  - when you need details on stack/dependencies it can be inferred though [gradlew build](build.gradle.kts)
  - infra can be inferred through [docker compose](compose.yaml)
  - env vars can be inferred through [.env](.env)
- don't modify this file on `/init` if you deem something important is worth adding inform me in STDOUT
- when I ask for **fresh eyes** spawn a subagent and pass it the **why** and **why not** we would have discussed earlier
- **never context poison fresh eyes it's job is to be critical not a confirmation bias agent for previous decisions**
- when I ask you to note something for future reference do it in [notes](docs/notes.md)
- when I ask you to note something as a todo do it in [todo](docs/todo.md)
- when working on [file ingestion pipeline](src/main/kotlin/com/hamza/springai/rag/pipeline/Functions.kt) always
  check [specs](docs/pipeline_specs_v1.md)
- always prefer Kotlin sugar (expression bodies, `let`, `also`, `apply`, destructuring, `when`, extension functions,
  trailing lambdas, ...etc.) over Java verbosity
