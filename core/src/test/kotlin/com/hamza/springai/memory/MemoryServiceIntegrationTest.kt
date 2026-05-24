package com.hamza.springai.memory

import com.hamza.springai.OllamaContainerConfig
import com.hamza.springai.QdrantContainerConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(QdrantContainerConfig::class, OllamaContainerConfig::class)
class MemoryServiceIntegrationTest {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Autowired
    private lateinit var service: IMemoryService

    @Autowired
    @Qualifier("chatMemoryVectorStore")
    private lateinit var vectorStore: VectorStore

    @Autowired
    private lateinit var jdbcClient: JdbcClient // FIXME: switch to JPA

    private val conversationId = "conversationId"

    private val prompt = "Who is Gandalf?"

    private data class MemoryRow(
        val type: String,
        val content: String,
    )

    @Test
    fun promptWithJdbcMemory() {
        // prompt
        service.promptWithJdbcMemory(conversationId, MemoryRequest(prompt))
        // check both prompt and response stored in jdbc backend
        val rows =
            jdbcClient
                .sql("SELECT type, content FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = :id ORDER BY timestamp")
                .param("id", conversationId)
                .query(MemoryRow::class.java)
                .list()
        rows.forEach { logger.debug("jdbc:  type={} content={}", it?.type, it?.content) }
        assertThat(rows).hasSize(2)
        assertThat(rows).anyMatch { it?.content == prompt }
        // vague prompt with same session
        var response = service.promptWithJdbcMemory(conversationId, MemoryRequest("is he alive?"))
        // LLM should remember
        assertThat(response.response).containsIgnoringCase("gandalf")
        // vague prompt with new session
        response = service.promptWithJdbcMemory("new", MemoryRequest("is he alive?"))
        // LLM should not remember
        assertThat(response.response).doesNotContainIgnoringCase("gandalf")
    }

    @Test
    fun promptWithVectorStoreMemory() {
        // prompt
        service.promptWithVectorStoreMemory(conversationId, MemoryRequest(prompt))
        // check both prompt and response stored in vector backend
        val documents =
            vectorStore.similaritySearch(
                SearchRequest
                    .builder()
                    .query(prompt)
                    .similarityThresholdAll()
                    .topK(100)
                    .filterExpression(FilterExpressionBuilder().eq("conversationId", conversationId).build())
                    .build(),
            )
        documents.forEach { logger.debug("vector:   type={} content={}", it.metadata["messageType"], it.text) }
        assertThat(documents).hasSize(2)
        assertThat(documents).anyMatch { it.text == prompt }
        // vague prompt with same session
        var response = service.promptWithVectorStoreMemory(conversationId, MemoryRequest("is he alive?"))
        // LLM should remember
        assertThat(response.response).containsIgnoringCase("gandalf")
        // vague prompt with new session
        response = service.promptWithVectorStoreMemory("new", MemoryRequest("is he alive?"))
        // LLM should not remember
        assertThat(response.response).doesNotContainIgnoringCase("gandalf")
    }
}
