import com.github.gradle.node.npm.task.NpmTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.owasp.dependencycheck.reporting.ReportGenerator.Format

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21" apply false
    id("org.springframework.boot") version "4.0.6" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.autonomousapps.dependency-analysis") version "3.14.0"
    id("com.github.ben-manes.versions") version "0.54.0"
    id("com.github.node-gradle.node") version "7.1.0"
    id("com.google.cloud.tools.jib") version "3.5.3" apply false
    id("org.graalvm.buildtools.native") version "1.1.1" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("org.owasp.dependencycheck") version "12.2.2"
    jacoco
}

allprojects {
    plugins.apply("org.jetbrains.kotlin.jvm")
    plugins.apply("com.autonomousapps.dependency-analysis")
    plugins.apply("com.github.ben-manes.versions")
    plugins.apply("org.jlleitschuh.gradle.ktlint")
    plugins.apply("org.owasp.dependencycheck")
    plugins.apply("jacoco")

    repositories { mavenCentral() }

    java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

    kotlin {
        compilerOptions {
            freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
        }
    }

    // https://nvd.nist.gov/developers/request-an-api-key
    dependencyCheck {
        data.directory = "${System.getProperty("user.home")}/owasp-data"
        format = Format.HTML.toString()
        nvd.apiKey = System.getenv("NVD_APIKEY") ?: ""
    }
}

subprojects {
    plugins.apply("org.jetbrains.kotlin.plugin.spring")
    plugins.apply("org.springframework.boot")
    plugins.apply("io.spring.dependency-management")
    plugins.apply("jacoco")

    val mockitoAgent: Configuration = configurations.create("mockitoAgent")
    val awaitilityVersion = "4.3.0"
    val junitPioneerVersion = "2.3.0"
    val mockitoCoreVersion = "5.23.0"
    val mockitoKotlinVersion = "6.3.0"
    val wiremockSpringBootVersion = "4.2.1"

    dependencies {
        implementation("org.jetbrains.kotlin:kotlin-reflect")
        implementation("tools.jackson.module:jackson-module-kotlin")

        mockitoAgent("org.mockito:mockito-core:$mockitoCoreVersion") { isTransitive = false }

        testImplementation("org.awaitility:awaitility-kotlin:$awaitilityVersion")
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
        testImplementation("org.junit-pioneer:junit-pioneer:$junitPioneerVersion")
        testImplementation("org.mockito.kotlin:mockito-kotlin:$mockitoKotlinVersion")
        testImplementation("org.wiremock.integrations:wiremock-spring-boot:$wiremockSpringBootVersion")

        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    tasks {
        withType<JavaCompile>().configureEach {
            options.encoding = Charsets.UTF_8.name()
            options.isFork = true
        }

        withType<Test>().configureEach {
            useJUnitPlatform()
            jvmArgumentProviders += CommandLineArgumentProvider { listOf("-javaagent:${mockitoAgent.asPath}") }
            maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
            forkEvery = 100
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
                classDirectories.files.map { fileTree(it) { exclude("**/ApplicationKt.class") } },
            )
            reports {
                csv.required = false
                html.outputLocation = layout.buildDirectory.dir("jacocoHtml")
                xml.required = false
            }
        }

        jar { enabled = false }
    }

    configure<KtlintExtension> {
        android.set(false)
        coloredOutput.set(true)
        debug.set(true)
        verbose.set(true)
        version.set("1.8.0")
    }
}

tasks {
    val npmRunFormat =
        register("npm_run_format", NpmTask::class) {
            description = "npm run format hook"
            args = listOf("run", "format")
        }

    processResources { dependsOn(npmRunFormat) }
}

node { download = true }
