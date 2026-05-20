package com.hamza.springai.prompt

import com.hamza.springai.OllamaContainerConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import reactor.test.StepVerifier

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(OllamaContainerConfig::class)
class PromptServiceIntegrationTest {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Autowired
    private lateinit var service: IPromptService

    @Value($$"${spring.ai.ollama.chat.model}")
    private lateinit var model: String

    private val prompt = "What is the capital of France?"

    @MockitoBean // prevent autoconf of vector store, not needed in this test
    private lateinit var vectorStore: VectorStore

    @Test
    fun `prompting LLM should return a string response`() {
        val response = service.prompt(PromptRequest(prompt))

        assertThat(response.prompt).isEqualTo(prompt)
        assertThat(response.response).isNotBlank

        logger.debug("model: `{}` # response: `{}`", model, response)
    }

    @Test
    fun `asking LLM to generate a list of songs should return a valid wrapper object`() {
        val response = service.songs(2006)

        assertThat(response).isInstanceOf(SongResponse::class.java)
        assertThat(response.response)
            .isNotEmpty
            .allSatisfy {
                assertThat(it).isInstanceOf(Song::class.java)
                assertThat(it.title).isNotBlank()
            }

        logger.debug("model: `{}` # response: `{}`", model, response)
    }

    @Test
    fun `asking LLM to generate a list of movies should return a flux of string`() {
        StepVerifier
            .create(service.movies(2013).collectList())
            .assertNext {
                assertThat(it.joinToString("")).isNotBlank()
                logger.debug("model: `{}` # response: `{}`", model, it.joinToString(", "))
            }.verifyComplete()
    }
}
