package com.hamza.springai.mcp

import com.hamza.springai.CurrencyMcpContainerConfig
import com.hamza.springai.FsMcpContainerConfig
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
@Import(OllamaContainerConfig::class, FsMcpContainerConfig::class, CurrencyMcpContainerConfig::class)
class McpServiceTest {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Autowired
    private lateinit var service: IMcpService

    private val filePrompt =
        "Can you list the content of /projects ? Return ONLY the names of all folders and files."

    private val currencyPrompt =
        "What's today's exchange rate between euro and Moroccan currency?"

    @MockitoBean // prevent autoconf of embedding vector store, not needed in this test
    private lateinit var vectorStore: VectorStore

    @MockitoBean("chatMemoryVectorStore") // prevent autoconf of memory vector store, not needed in this test
    private lateinit var chatMemoryVectorStore: VectorStore

    // retry N times because small models are unreliable
    @RetryingTest(maxAttempts = 5, suspendForMs = 1000)
    fun promptForFiles() {
        StepVerifier
            .create(service.prompt(McpRequest(filePrompt)).collectList())
            .assertNext {
                val response = it.joinToString("")
                assertThat(response)
                    .containsIgnoringCase("amal")
                    .containsIgnoringCase("hope")
                    .containsIgnoringCase("espoir")
                logger.debug("files: {}", response)
            }.verifyComplete()
    }

    // retry N times because small models are unreliable
    @RetryingTest(maxAttempts = 5, suspendForMs = 1000)
    fun promptForCurrency() {
        StepVerifier
            .create(service.prompt(McpRequest(currencyPrompt)).collectList())
            .assertNext {
                val response = it.joinToString("")
                assertThat(response)
                    .containsIgnoringCase("EUR")
                    .containsIgnoringCase("MAD")
                    .containsIgnoringCase("rate")
                    .containsIgnoringCase("=")
                logger.debug("currency: {}", response)
            }.verifyComplete()
    }
}
