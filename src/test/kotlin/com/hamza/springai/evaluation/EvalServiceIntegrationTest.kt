package com.hamza.springai.evaluation

import com.hamza.springai.OllamaContainerConfig
import org.assertj.core.api.Assertions.assertThat
import org.junitpioneer.jupiter.RetryingTest
import org.slf4j.LoggerFactory
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(OllamaContainerConfig::class)
class EvalServiceIntegrationTest {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Autowired
    private lateinit var service: IEvalService

    @Value($$"${spring.ai.ollama.chat.model}")
    private lateinit var model: String

    private val prompt = "What is the capital of France?"

    @MockitoBean // prevent autoconf of embedding vector store, not needed in this test
    private lateinit var vectorStore: VectorStore

    @MockitoBean
    @Qualifier("chatMemoryVectorStore") // prevent autoconf of memory vector store, not needed in this test
    private lateinit var chatMemoryVectorStore: VectorStore

    // FIXME: retry N times because small models are unreliable
    @RetryingTest(maxAttempts = 5, suspendForMs = 1000)
    fun evaluate() {
        val response = "The capital of France is Paris."

        val evaluation = service.eval(EvalRequest(prompt, response))

        logger.debug("evaluation: {}", evaluation)

        assertThat(evaluation.prompt).isEqualTo(prompt)
        assertThat(evaluation.response).isEqualTo(response)
        assertThat(evaluation.evaluation.pass).isTrue
        assertThat(evaluation.evaluation.score).isGreaterThanOrEqualTo(0.5f)
    }

    // FIXME: retry N times because small models are unreliable
    @RetryingTest(maxAttempts = 5, suspendForMs = 1000)
    fun `evaluate wrong`() {
        val response = "The sky is blue because apples."

        val evaluation = service.eval(EvalRequest(prompt, response))

        logger.debug("evaluation: {}", evaluation)

        assertThat(evaluation.prompt).isEqualTo(prompt)
        assertThat(evaluation.response).isEqualTo(response)
        assertThat(evaluation.evaluation.pass).isFalse
        assertThat(evaluation.evaluation.score).isLessThan(0.5f)
    }
}
