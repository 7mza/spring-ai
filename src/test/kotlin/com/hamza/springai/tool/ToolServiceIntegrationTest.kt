package com.hamza.springai.tool

import com.hamza.springai.OllamaContainerConfig
import com.hamza.springai.rag.file.File
import com.hamza.springai.rag.file.IFileRepo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean

@Disabled // test LLMs bad
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(OllamaContainerConfig::class)
class ToolServiceIntegrationTest {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Autowired
    private lateinit var service: IToolService

    @Autowired
    private lateinit var repo: IFileRepo

    @MockitoBean // prevent autoconf of embedding vector store, not needed in this test
    private lateinit var vectorStore: VectorStore

    @MockitoBean
    @Qualifier("chatMemoryVectorStore") // prevent autoconf of memory vector store, not needed in this test
    private lateinit var chatMemoryVectorStore: VectorStore

    @Test
    fun getCurrentTimeAt() {
        val response = service.getCurrentTimeAt("Riyadh")
        logger.debug("response: {}", response)
        assertThat(response).isNotBlank
        assertThat(response).containsIgnoringCase("Riyadh")
        assertThat(response).containsIgnoringCase("time")
    }

    @Test
    fun listIngestedFiles() {
        repo.saveAll(
            listOf(
                File(name = "file1", hash = "hash1"),
                File(name = "file2", hash = "hash2"),
                File(name = "file3", hash = "hash3"),
            ),
        )
        val response = service.listIngestedFiles(size = 10, page = 0)
        logger.debug("response: {}", response)
        assertThat(response.content).hasSize(3)
        assertThat(response.page.isFirst).isTrue
        assertThat(response.page.isLast).isTrue
        assertThat(response.page.number).isZero
        assertThat(response.page.size).isEqualTo(10)
        assertThat(response.page.totalPages).isOne
        assertThat(response.page.totalElements).isEqualTo(3)
        assertThat(response.sort).isEmpty()
    }
}
