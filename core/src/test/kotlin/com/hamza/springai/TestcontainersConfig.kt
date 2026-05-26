package com.hamza.springai

import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.DeviceRequest
import com.github.dockerjava.api.model.Volume
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.context.annotation.Conditional
import org.springframework.core.type.AnnotatedTypeMetadata
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.ollama.OllamaContainer
import org.testcontainers.qdrant.QdrantContainer
import org.testcontainers.utility.DockerImageName
import java.nio.file.Files

private class NvidiaRuntimeAvailable : Condition {
    override fun matches(
        context: ConditionContext,
        metadata: AnnotatedTypeMetadata,
    ): Boolean =
        runCatching {
            DockerClientFactory
                .instance()
                .client()
                .infoCmd()
                .exec()
                .runtimes
                ?.containsKey("nvidia") == true
        }.getOrDefault(false)
}

@TestConfiguration(proxyBeanMethods = false)
class MinioTestContainerConfig {
    @Bean
    fun minioContainer(): MinIOContainer = MinIOContainer(DockerImageName.parse("minio/minio:latest"))

    @Bean
    fun mProperties(minioContainer: MinIOContainer): DynamicPropertyRegistrar =
        DynamicPropertyRegistrar {
            it.add("spring.cloud.aws.credentials.access-key") { minioContainer.userName }
            it.add("spring.cloud.aws.credentials.secret-key") { minioContainer.password }
            it.add("spring.cloud.aws.s3.endpoint") { minioContainer.s3URL }
        }
}

@TestConfiguration(proxyBeanMethods = false)
class OllamaContainerConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    @ServiceConnection
    @Conditional(NvidiaRuntimeAvailable::class)
    fun ollamaContainerGpu(): OllamaContainer {
        logger.info("nvidia runtime detected, starting ollama with gpu")
        return OllamaContainer(DockerImageName.parse("ollama/ollama:latest"))
            .withEnv("OLLAMA_MAX_LOADED_MODELS", "2")
            .withEnv("OLLAMA_NUM_PARALLEL", "1")
            .withCreateContainerCmdModifier {
                it.hostConfig!!
                    .withDeviceRequests(
                        listOf(
                            DeviceRequest()
                                .withCapabilities(listOf(listOf("gpu")))
                                .withDeviceIds(listOf("nvidia.com/gpu=all"))
                                .withDriver("cdi"),
                        ),
                    ).withBinds(Bind("sa_ollama_data", Volume("/root/.ollama")))
            }
    }

    @Bean
    @ServiceConnection
    @ConditionalOnMissingBean(OllamaContainer::class)
    fun ollamaContainerCpu(): OllamaContainer {
        logger.info("nvidia runtime not available, starting ollama with cpu")
        return OllamaContainer(DockerImageName.parse("ollama/ollama:latest"))
            .withEnv("OLLAMA_MAX_LOADED_MODELS", "2")
            .withEnv("OLLAMA_NUM_PARALLEL", "1")
            .withCreateContainerCmdModifier {
                it.hostConfig!!.withBinds(Bind("sa_ollama_data", Volume("/root/.ollama")))
            }
    }
}

@TestConfiguration(proxyBeanMethods = false)
class QdrantContainerConfig {
    @Bean
    @ServiceConnection
    fun qdrantContainer(): QdrantContainer = QdrantContainer(DockerImageName.parse("qdrant/qdrant:latest"))
}

@TestConfiguration(proxyBeanMethods = false)
class FsMcpContainerConfig {
    private fun createFakeTree(): String =
        Files
            .createTempDirectory("fake_tree_")
            .also {
                Files.createDirectory(it.resolve("amal"))
                Files.writeString(it.resolve("amal/2006.txt"), "hello")
                Files.createDirectory(it.resolve("hope"))
                Files.writeString(it.resolve("hope/2010.md"), "# bye")
                Files.writeString(it.resolve("espoir.md"), "# in the next")
                // remove tmp folder at jvm shutdown
                Runtime.getRuntime().addShutdownHook(Thread { it.toFile().deleteRecursively() })
            }.toString()

    @Bean
    fun fsMcpContainer(): GenericContainer<*> =
        GenericContainer(DockerImageName.parse("node:lts-alpine"))
            .withCommand(
                "/bin/sh",
                "-c",
                """
                npx --yes supergateway --stdio 'npx --yes @modelcontextprotocol/server-filesystem /projects' \
                --outputTransport streamableHttp --port 9962 --healthEndpoint /health
                """.trimIndent(),
            ).withExposedPorts(9962)
            .withFileSystemBind(createFakeTree(), "/projects", BindMode.READ_ONLY)
            .waitingFor(Wait.forHttp("/health").forResponsePredicate { it.contains("ok") })

    @Bean
    fun fProperties(fsMcpContainer: GenericContainer<*>): DynamicPropertyRegistrar =
        DynamicPropertyRegistrar {
            it.add("spring.ai.mcp.client.streamable-http.connections.filesystem.url") {
                "http://${fsMcpContainer.host}:${fsMcpContainer.getMappedPort(9962)}"
            }
        }
}

@TestConfiguration(proxyBeanMethods = false)
class CurrencyMcpContainerConfig {
    @Bean
    fun currencyMcpContainer(): GenericContainer<*> =
        GenericContainer(DockerImageName.parse("mcp-currency:latest"))
            .withExposedPorts(3002)
            .waitingFor(Wait.forHttp("/actuator/health/liveness").forResponsePredicate { it.contains("UP") })
            .withImagePullPolicy { false }

    @Bean
    fun cProperties(currencyMcpContainer: GenericContainer<*>): DynamicPropertyRegistrar =
        DynamicPropertyRegistrar {
            it.add("spring.ai.mcp.client.streamable-http.connections.mcp-currency.url") {
                "http://${currencyMcpContainer.host}:${currencyMcpContainer.getMappedPort(3002)}"
            }
        }
}
