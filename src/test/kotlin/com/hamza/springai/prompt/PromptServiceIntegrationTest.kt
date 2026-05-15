package com.hamza.springai.prompt

import com.hamza.springai.OllamaContainer
import com.hamza.springai.promptEvaluationFailMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junitpioneer.jupiter.RetryingTest
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator
import org.springframework.ai.chat.evaluation.RelevancyEvaluator
import org.springframework.ai.evaluation.EvaluationRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(OllamaContainer::class)
class PromptServiceIntegrationTest {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Autowired
    private lateinit var service: IPromptService

    @Autowired
    private lateinit var chatClientBuilder: ChatClient.Builder

    private lateinit var relevancyEvaluator: RelevancyEvaluator
    private lateinit var factsEvaluator: FactCheckingEvaluator

    @Value($$"${spring.ai.ollama.chat.model}")
    private lateinit var model: String

    private val prompt = "Is water wet?"

    @BeforeEach
    fun beforeEach() {
        relevancyEvaluator =
            RelevancyEvaluator
                .builder()
                .chatClientBuilder(chatClientBuilder)
                .build()
        factsEvaluator =
            FactCheckingEvaluator
                .builder(chatClientBuilder)
                .evaluationPrompt(prompt)
                .build()
    }

    // FIXME: retry N times because small models are unreliable
    @RetryingTest(5)
    fun `prompt and evaluate response manually using RelevancyEvaluator`() {
        val response = service.prompt(PromptRequest(prompt))
        assertThat(response.response).isNotNull
        assertThat(response.response!!).isNotEmpty
        assertThat(response.response).isNotBlank
        logger.debug("model `{}` : {}", model, response.response)
        val evaluation = relevancyEvaluator.evaluate(EvaluationRequest(prompt, response.response))
        assertThat(evaluation.isPass)
            .withFailMessage(promptEvaluationFailMessage, prompt, response.response)
            .isTrue
    }

    // FIXME: retry N times because small models are unreliable
    @RetryingTest(5)
    fun `prompt and evaluate response manually using RelevancyEvaluator, with non matching prompt and answer`() {
        val response = service.prompt(PromptRequest(prompt))
        assertThat(response.response).isNotNull
        assertThat(response.response!!).isNotEmpty
        assertThat(response.response).isNotBlank
        val evaluation =
            relevancyEvaluator.evaluate(
                EvaluationRequest("What is the capital of France?", response.response),
            )
        assertThat(evaluation.isPass)
            .withFailMessage(promptEvaluationFailMessage, prompt, response.response)
            .isFalse
    }

    @Test
    fun `prompt and evaluate response automatically using service`() {
        val response = service.prompt(PromptRequest(prompt), true)
        assertThat(response.response).isNotNull
        assertThat(response.response!!).isNotEqualTo(PROMPT_EVALUATION_RECOVERY_MESSAGE)
    }
}
