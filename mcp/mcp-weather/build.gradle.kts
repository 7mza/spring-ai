plugins {
    // id("org.graalvm.buildtools.native")
}

group = "com.hamza.springai.mcp"
version = "0.0.1-SNAPSHOT"

dependencies {
    implementation("org.springframework.ai:spring-ai-autoconfigure-mcp-server-common") // McpServerStdioDisabledCondition
    implementation("org.springframework.ai:spring-ai-starter-mcp-server")
    implementation("org.springframework.boot:spring-boot-starter-restclient")

    testImplementation("org.springframework.boot:spring-boot-starter-restclient-test")
}

val springAiVersion = "2.0.0-M7"

dependencyManagement { imports { mavenBom("org.springframework.ai:spring-ai-bom:$springAiVersion") } }

tasks {
    bootRun { enabled = false }

    // withType<ProcessAot>().configureEach { enabled = project.hasProperty("aot") }
    // withType<ProcessTestAot>().configureEach { enabled = project.hasProperty("aot") }
    // withType<CollectReachabilityMetadata>().configureEach { enabled = project.hasProperty("aot") }
}
