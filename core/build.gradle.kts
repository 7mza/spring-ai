plugins {
    kotlin("plugin.jpa") version "2.3.21"
    id("com.google.cloud.tools.jib")
    // id("org.graalvm.buildtools.native")
    // id("org.hibernate.orm") version "7.2.12.Final"
    id("org.springdoc.openapi-gradle-plugin") version "1.9.0"
}

group = "com.hamza.springai"
version = "0.0.1-SNAPSHOT"

val hypersistenceTsidVersion = "2.1.4"
val logbookSpringVersion = "4.0.4"
val openapiVersion = "3.0.3"
val springRetryVersion = "2.0.12"

dependencies {
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.ai:spring-ai-spring-boot-docker-compose")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    implementation("io.awspring.cloud:spring-cloud-aws-starter-s3")
    implementation("io.hypersistence:hypersistence-tsid:$hypersistenceTsidVersion")
    implementation("org.ehcache:ehcache::jakarta")
    implementation("org.hibernate.orm:hibernate-jcache")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$openapiVersion")
    implementation("org.springframework.ai:spring-ai-advisors-vector-store")
    implementation("org.springframework.ai:spring-ai-autoconfigure-mcp-client-common") // McpSseClientProperties
    implementation("org.springframework.ai:spring-ai-rag")
    implementation("org.springframework.ai:spring-ai-starter-mcp-client-webflux") {
        // streamableHttp = mode shouldn't matter
        // whether sync or async servers, just use webflux mcp client + mark all clients async + hit servers on / not /mcp
    }
    implementation("org.springframework.ai:spring-ai-starter-model-chat-memory-repository-jdbc")
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("org.springframework.ai:spring-ai-starter-vector-store-qdrant")
    implementation("org.springframework.ai:spring-ai-tika-document-reader")
    implementation("org.springframework.boot:spring-boot-h2console")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.cloud:spring-cloud-function-context")
    implementation("org.springframework.integration:spring-integration-file")
    implementation("org.springframework.retry:spring-retry:$springRetryVersion")
    implementation("org.zalando:logbook-spring-boot-starter:$logbookSpringVersion")

    runtimeOnly("com.h2database:h2")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.ai:spring-ai-spring-boot-testcontainers")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-restclient-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-minio")
    testImplementation("org.testcontainers:testcontainers-ollama")
    testImplementation("org.testcontainers:testcontainers-qdrant")
}

val springAiVersion = "2.0.0-M8"
val springAwsVersion = "4.0.2"
val springCloudVersion = "2025.1.1"

dependencyManagement {
    imports {
        mavenBom("io.awspring.cloud:spring-cloud-aws-dependencies:$springAwsVersion")
        mavenBom("org.springframework.ai:spring-ai-bom:$springAiVersion")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion")
    }
}

tasks {
    bootJar { archiveBaseName = "spring-ai" }

    bootRun {
        dependsOn(":mcp:mcp-currency:jibDockerBuild")
        dependsOn(":mcp:mcp-weather:bootJar")
        workingDir = rootProject.projectDir
    }

    test { dependsOn(":mcp:mcp-currency:jibDockerBuild") }

    // withType<ProcessAot>().configureEach { enabled = project.hasProperty("aot") }
    // withType<ProcessTestAot>().configureEach { enabled = project.hasProperty("aot") }
    // withType<CollectReachabilityMetadata>().configureEach { enabled = project.hasProperty("aot") }

    jibDockerBuild { dependsOn(build) }

    withType<Test>().configureEach {
        maxParallelForks = 2 // if tests are slow by nature (LLM), more workers = worse
    }
}

// hibernate { enhancement { enableAssociationManagement = true } }

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

openApi {
    apiDocsUrl.set("http://localhost:7355/api-docs.yaml")
    customBootRun { args.set(listOf("--spring.profiles.active=openapi-plugin")) }
    outputDir.set(rootProject.layout.projectDirectory.dir("docs"))
    outputFileName.set("api-docs.yaml")
    waitTimeInSeconds.set(60)
}

jib {
    from { image = "eclipse-temurin:25-jre-alpine" }
    to {
        image = "spring-ai"
        tags = setOf("latest", project.version.toString())
    }
    container { ports = listOf("80") }
}

springBoot { buildInfo() }
