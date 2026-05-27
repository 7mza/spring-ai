plugins {
    id("com.google.cloud.tools.jib")
    // id("org.graalvm.buildtools.native")
}

group = "com.hamza.springai.mcp"
version = "0.0.1-SNAPSHOT"

dependencies {
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    implementation("org.springframework.ai:spring-ai-autoconfigure-mcp-server-common") // McpServerStdioDisabledCondition
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-webclient")
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webclient-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
}

val springAiVersion = "2.0.0-M8"

dependencyManagement { imports { mavenBom("org.springframework.ai:spring-ai-bom:$springAiVersion") } }

tasks {
    bootRun { enabled = false }

    // withType<ProcessAot>().configureEach { enabled = project.hasProperty("aot") }
    // withType<ProcessTestAot>().configureEach { enabled = project.hasProperty("aot") }
    // withType<CollectReachabilityMetadata>().configureEach { enabled = project.hasProperty("aot") }
}

jib {
    from { image = "eclipse-temurin:25-jre-alpine" }
    to { tags = setOf("latest") }
    container { ports = listOf("80") }
}

springBoot { buildInfo() }
