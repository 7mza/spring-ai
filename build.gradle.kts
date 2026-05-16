import com.github.gradle.node.npm.task.NpmTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.owasp.dependencycheck.gradle.extension.DependencyCheckExtension
import org.owasp.dependencycheck.reporting.ReportGenerator.Format
import org.springframework.boot.gradle.tasks.aot.ProcessAot
import org.springframework.boot.gradle.tasks.aot.ProcessTestAot

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"

    id("com.autonomousapps.dependency-analysis") version "3.12.0"
    id("com.github.ben-manes.versions") version "0.54.0"
    id("com.github.node-gradle.node") version "7.1.0"
    id("com.google.cloud.tools.jib") version "3.5.3"
    id("org.graalvm.buildtools.native") version "1.1.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("org.owasp.dependencycheck") version "12.2.2"
    jacoco
}

group = "com.hamza"
version = "0.0.1-SNAPSHOT"

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

repositories { mavenCentral() }

extra["springAiVersion"] = "2.0.0-M6"
extra["springCloudVersion"] = "2025.1.1"

val mockitoAgent: Configuration = configurations.create("mockitoAgent")

val awaitilityVersion = "4.3.0"
val junitPioneerVersion = "2.3.0"
val mockitoCoreVersion = "5.23.0"
val mockitoKotlinVersion = "6.3.0"
val openapiVersion = "3.0.3"
val picocliVersion = "4.7.7"
val springRetryVersion = "2.0.12"
val wiremockSpringBootVersion = "4.2.1"

dependencies {
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    developmentOnly("org.springframework.ai:spring-ai-spring-boot-docker-compose")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    // implementation("info.picocli:picocli-spring-boot-starter:$picocliVersion")
    implementation("org.eclipse.jetty.http2:jetty-http2-server")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$openapiVersion")
    // implementation("org.springframework.ai:spring-ai-advisors-vector-store")
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    // implementation("org.springframework.ai:spring-ai-starter-vector-store-qdrant")
    // implementation("org.springframework.ai:spring-ai-tika-document-reader")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
    implementation("org.springframework.boot:spring-boot-starter-jetty")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation("org.springframework.cloud:spring-cloud-function-web")
    implementation("org.springframework.retry:spring-retry:$springRetryVersion")
    implementation("tools.jackson.module:jackson-module-kotlin")

    mockitoAgent("org.mockito:mockito-core:$mockitoCoreVersion") { isTransitive = false }

    testImplementation("org.awaitility:awaitility-kotlin:$awaitilityVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit-pioneer:junit-pioneer:$junitPioneerVersion")
    testImplementation("org.mockito.kotlin:mockito-kotlin:$mockitoKotlinVersion")
    testImplementation("org.springframework.ai:spring-ai-spring-boot-testcontainers")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-restclient-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-ollama")
    testImplementation("org.testcontainers:testcontainers-qdrant")
    testImplementation("org.wiremock.integrations:wiremock-spring-boot:$wiremockSpringBootVersion")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = Charsets.UTF_8.name()
        options.isFork = true
        options.isIncremental = true
    }

    withType<Test>().configureEach {
        useJUnitPlatform()
        jvmArgs("-javaagent:${mockitoAgent.asPath}")
        maxParallelForks = 2
        // forkEvery = 100
        reports {
            html.required = false
            junitXml.required = false
        }
        testLogging {
            events = setOf(FAILED)
            exceptionFormat = FULL
            showCauses = true
            showExceptions = true
            showStackTraces = true
            showStandardStreams = false
        }
        finalizedBy(jacocoTestReport)
        extensions.configure<JacocoTaskExtension> {
            excludes = listOf("jdk.internal.*")
            isIncludeNoLocationClasses = true
        }
    }

    jacocoTestReport {
        dependsOn(test)
        classDirectories.setFrom(
            files(
                classDirectories.files.map {
                    fileTree(it) {
                        exclude("**/ApplicationKt.class")
                    }
                },
            ),
        )
        reports {
            csv.required = false
            html.outputLocation = layout.buildDirectory.dir("jacocoHtml")
            xml.required = false
        }
    }

    jar { enabled = false }

    jibDockerBuild { dependsOn(build) }

    withType<ProcessAot>().configureEach { enabled = project.hasProperty("aot") }

    withType<ProcessTestAot>().configureEach { enabled = project.hasProperty("aot") }

    processResources { dependsOn("npm_run_format") }

    register("npm_run_format", NpmTask::class) {
        description = ""
        args = listOf("run", "format")
    }
}

configure<KtlintExtension> {
    android.set(false)
    coloredOutput.set(true)
    debug.set(true)
    verbose.set(true)
    version.set("1.8.0")
}

configure<DependencyCheckExtension> { format = Format.HTML.toString() }

jib {
    from { image = "eclipse-temurin:25-jre-alpine" }
    to { tags = setOf("latest") }
    container { ports = listOf("80") }
}

springBoot { buildInfo() }

// https://nvd.nist.gov/developers/request-an-api-key
dependencyCheck { nvd.apiKey = System.getenv("NVD_APIKEY") ?: "" }

node { download = true }
