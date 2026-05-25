package com.hamza.springai.mcp

import com.hamza.springai.MCPFSContainerConfig
import com.hamza.springai.OllamaContainerConfig
import org.assertj.core.api.Assertions.assertThat
import org.junitpioneer.jupiter.RetryingTest
import org.slf4j.LoggerFactory
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import reactor.test.StepVerifier

/* these tests r redundant & covered by sBoot own tests
 * they are just used as building blocks for future work & to detect regression when updating
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(OllamaContainerConfig::class, MCPFSContainerConfig::class)
class McpServiceTest {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Autowired
    private lateinit var service: IMcpService

    private val prompt =
        "Can you list the content of /projects ? Return ONLY the names of all folders and files."

    @MockitoBean // prevent autoconf of embedding vector store, not needed in this test
    private lateinit var vectorStore: VectorStore

    @MockitoBean("chatMemoryVectorStore") // prevent autoconf of memory vector store, not needed in this test
    private lateinit var chatMemoryVectorStore: VectorStore

    // retry N times because small models are unreliable
    @RetryingTest(maxAttempts = 5, suspendForMs = 1000)
    fun promptForFiles() {
        StepVerifier
            .create(service.prompt(McpRequest(prompt)).collectList())
            .assertNext {
                assertThat(it.joinToString(""))
                    .containsIgnoringCase("amal")
                    .containsIgnoringCase("hope")
                    .containsIgnoringCase("espoir")
                logger.debug("files: {}", it.joinToString())
            }.verifyComplete()
    }
}
