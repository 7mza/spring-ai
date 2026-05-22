package com.hamza.springai.tool

import com.hamza.springai.OllamaContainerConfig
import com.hamza.springai.rag.file.File
import com.hamza.springai.rag.file.IFileRepo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.slf4j.LoggerFactory
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(OllamaContainerConfig::class)
class ToolServiceIntegrationTest {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Autowired
    private lateinit var service: IToolService

    @Autowired
    private lateinit var repo: IFileRepo

    @MockitoSpyBean
    private lateinit var tools: ITools

    @MockitoBean // prevent autoconf of embedding vector store, not needed in this test
    private lateinit var vectorStore: VectorStore

    @MockitoBean
    @Qualifier("chatMemoryVectorStore") // prevent autoconf of memory vector store, not needed in this test
    private lateinit var chatMemoryVectorStore: VectorStore

    @Test
    fun getCurrentTimeAt() {
        // tell llm to call tool
        val response = service.getCurrentTimeAt("Riyadh")
        logger.debug("getCurrentTimeAt: {}", response)
        // check tool was called
        val captor = argumentCaptor<String>()
        verify(tools).getCurrentTimeAt(captor.capture())
        assertThat(captor.firstValue).satisfiesAnyOf(
            { assertThat(it).containsIgnoringCase("Riyadh") },
            { assertThat(it).containsIgnoringCase("Asia") },
        )
        // dont care about response
    }

    @Test
    fun listIngestedFiles() {
        // init DB to lower LLM hallucination
        repo.saveAll(
            listOf(
                File(name = "file1", hash = "hash1"),
                File(name = "file2", hash = "hash2"),
                File(name = "file3", hash = "hash3"),
            ),
        )
        // tell llm to call tool
        val response = service.listIngestedFiles(size = 10, page = 0)
        logger.debug("listIngestedFiles: {}", response)
        // check tool was called
        verify(tools).listIngestedFiles(10, 0)
        // dont care about response
    }
}
