package com.hamza.springai.config

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.document.Document
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.filter.Filter
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.client.RestClient

// mirror jpa create-drop: wipe vector/memory stores on shutdown
@Configuration
@ConditionalOnProperty(name = ["spring.jpa.hibernate.ddl-auto"], havingValue = "create-drop")
class CleaningConfigs(
    private val restClientBuilder: RestClient.Builder,
    @Value($$"${spring.ai.vectorstore.qdrant.host}") private val host: String,
    @Value($$"${spring.ai.vectorstore.qdrant.collection-name}") private val embeddingStoreName: String,
    @Value($$"${custom.memory.store}") private val chatMemoryStore: String,
    private val jdbcTemplate: JdbcTemplate,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @EventListener(ContextClosedEvent::class)
    fun clean() {
        wipeStore(embeddingStoreName)
        wipeStore(chatMemoryStore)
        runCatching {
            jdbcTemplate.execute("delete from SPRING_AI_CHAT_MEMORY")
            logger.trace("wiped table '{}'", "SPRING_AI_CHAT_MEMORY")
        }.onFailure { logger.trace("error wiping table 'SPRING_AI_CHAT_MEMORY': {}", it.message) }
    }

    private fun wipeStore(name: String) {
        runCatching {
            restClientBuilder
                .baseUrl("http://$host:6333")
                .build()
                .delete()
                .uri("/collections/{name}", name)
                .retrieve()
                .toBodilessEntity()
            logger.trace("wiped qdrant collection '{}'", name)
        }.onFailure { logger.trace("error wiping qdrant collection '{}': {}", name, it.message) }
    }
}

@Configuration // FIXME: ugly
@Profile("openapi-plugin")
class OpenapiPluginConfigs {
    @Bean
    fun noOpChatModel(): ChatModel = ChatModel { throw NotImplementedError() }

    @Bean
    fun noOpEmbeddingModel(): EmbeddingModel =
        object : EmbeddingModel {
            override fun call(request: EmbeddingRequest): EmbeddingResponse = throw NotImplementedError()

            override fun embed(document: Document): FloatArray = throw NotImplementedError()
        }

    @Bean(name = ["chatMemoryVectorStore"], defaultCandidate = false)
    fun noOpVectorStore(): VectorStore =
        object : VectorStore {
            override fun add(documents: List<Document>): Unit = throw NotImplementedError()

            override fun delete(idList: List<String>): Unit = throw NotImplementedError()

            override fun delete(filterExpression: Filter.Expression): Unit = throw NotImplementedError()

            override fun similaritySearch(request: SearchRequest): List<Document> = throw NotImplementedError()
        }
}
