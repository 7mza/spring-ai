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
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.ollama.OllamaContainer
import org.testcontainers.qdrant.QdrantContainer
import org.testcontainers.utility.DockerImageName

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
    fun minioProperties(minioContainer: MinIOContainer): DynamicPropertyRegistrar =
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
        logger.debug("nvidia runtime detected, starting ollama with gpu")
        return OllamaContainer(DockerImageName.parse("ollama/ollama:latest"))
            .withEnv("OLLAMA_NUM_PARALLEL", "4")
            .withEnv("OLLAMA_MAX_LOADED_MODELS", "1")
            .withCreateContainerCmdModifier {
                it.hostConfig!!
                    .withDeviceRequests(
                        listOf(
                            DeviceRequest()
                                .withDriver("nvidia")
                                .withCount(1)
                                .withCapabilities(listOf(listOf("gpu"))),
                        ),
                    ).withBinds(Bind("ollama_data", Volume("/root/.ollama")))
            }
    }

    @Bean
    @ServiceConnection
    @ConditionalOnMissingBean(OllamaContainer::class)
    fun ollamaContainerCpu(): OllamaContainer {
        logger.debug("nvidia runtime not available, starting ollama with cpu")
        return OllamaContainer(DockerImageName.parse("ollama/ollama:latest"))
            .withEnv("OLLAMA_NUM_PARALLEL", "2")
            .withEnv("OLLAMA_MAX_LOADED_MODELS", "1")
            .withCreateContainerCmdModifier {
                it.hostConfig!!.withBinds(Bind("ollama_data", Volume("/root/.ollama")))
            }
    }
}

@TestConfiguration(proxyBeanMethods = false)
class QdrantContainerConfig {
    @Bean
    @ServiceConnection
    fun qdrantContainer(): QdrantContainer = QdrantContainer(DockerImageName.parse("qdrant/qdrant:latest"))
}
