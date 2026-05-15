package com.hamza.springai.prompt

import com.hamza.springai.OllamaContainerWithGpu
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junitpioneer.jupiter.RetryingTest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(OllamaContainerWithGpu::class)
class PromptServiceIntegrationTest {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Autowired
    private lateinit var service: IPromptService

    @Value($$"${spring.ai.ollama.chat.model}")
    private lateinit var model: String

    private val prompt = "What is the capital of France?"

    @Test
    fun prompt() {
        val response = service.prompt(PromptRequest(prompt))

        assertThat(response.response).isNotNull
        assertThat(response.response!!).isNotEmpty
        assertThat(response.response).isNotBlank

        logger.debug("model: `{}` # prompt: `{}` # response: `{}`", model, prompt, response.response)
    }

    // FIXME: retry N times because small models are unreliable
    @Execution(ExecutionMode.CONCURRENT)
    @RetryingTest(maxAttempts = 5, suspendForMs = 1000)
    fun evaluate() {
        val response = "The capital of France is Paris."

        val evaluation = service.evaluate(EvaluateRequest(prompt, response))

        logger.debug("evaluation: {}", evaluation)

        assertThat(evaluation.pass).isTrue
        assertThat(evaluation.score).isGreaterThanOrEqualTo(0.5f)
    }

    // FIXME: retry N times because small models are unreliable
    @Execution(ExecutionMode.CONCURRENT)
    @RetryingTest(maxAttempts = 5, suspendForMs = 1000)
    fun `evaluate wrong`() {
        val response = "The sky is blue because apples."

        val evaluation = service.evaluate(EvaluateRequest(prompt, response))

        logger.debug("evaluation: {}", evaluation)

        assertThat(evaluation.pass).isFalse
        assertThat(evaluation.score).isLessThanOrEqualTo(0.5f)
    }
}
