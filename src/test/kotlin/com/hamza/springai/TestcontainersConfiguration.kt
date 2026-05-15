package com.hamza.springai

import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Volume
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.BindMode
import org.testcontainers.ollama.OllamaContainer
import org.testcontainers.qdrant.QdrantContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class OllamaContainer {
    @Bean
    @ServiceConnection
    fun ollamaContainer(): OllamaContainer = OllamaContainer(DockerImageName.parse("ollama/ollama:latest"))
}

@TestConfiguration(proxyBeanMethods = false)
class QdrantContainer {
    @Bean
    @ServiceConnection
    fun qdrantContainer(): QdrantContainer = QdrantContainer(DockerImageName.parse("qdrant/qdrant:latest"))
}
