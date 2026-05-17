package com.hamza.springai

import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.DeviceRequest
import com.github.dockerjava.api.model.Volume
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.ollama.OllamaContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class OllamaContainer {
    @Bean
    @ServiceConnection
    fun ollamaContainer(): OllamaContainer = OllamaContainer(DockerImageName.parse("ollama/ollama:latest"))
}

@TestConfiguration(proxyBeanMethods = false)
class OllamaContainerWithGpu {
    @Bean
    @ServiceConnection
    fun ollamaContainer(): OllamaContainer =
        OllamaContainer(DockerImageName.parse("ollama/ollama:latest"))
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

@TestConfiguration(proxyBeanMethods = false)
class PgContainer {
    @Bean
    @ServiceConnection
    fun pqContainer(): PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg18"))
}
