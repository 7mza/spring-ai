package com.hamza.springai.prompt

import com.hamza.springai.TestcontainersConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import reactor.test.StepVerifier

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig::class)
class PromptServiceIntegrationTest {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Autowired
    private lateinit var service: IPromptService

    @Value($$"${spring.ai.ollama.chat.model}")
    private lateinit var model: String

    private val prompt = "What is the capital of France?"

    @Test
    fun prompt() {
        val response = service.prompt(PromptRequest(prompt))

        assertThat(response.prompt).isEqualTo(prompt)
        assertThat(response.response).isNotBlank

        logger.debug("model: `{}` # response: `{}`", model, response)
    }

    @Test
    fun songs() {
        val response = service.songs(2006)

        assertThat(response.response).isNotEmpty

        logger.debug("model: `{}` # response: `{}`", model, response)
    }

    @Test
    fun movies() {
        StepVerifier
            .create(service.movies(2013))
            .recordWith(::mutableListOf)
            .expectNextCount(1) // at least 1
            .thenConsumeWhile { true }
            .consumeRecordedWith { logger.debug("model: `{}` # response: `{}`", model, it.joinToString("")) }
            .verifyComplete()
    }
}
