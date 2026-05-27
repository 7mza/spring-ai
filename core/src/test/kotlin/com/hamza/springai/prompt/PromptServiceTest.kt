package com.hamza.springai.prompt

import com.github.tomakehurst.wiremock.client.WireMock
import com.hamza.springai.OllamaContainerConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.wiremock.spring.ConfigureWireMock
import org.wiremock.spring.EnableWireMock
import reactor.test.StepVerifier

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(OllamaContainerConfig::class)
class PromptServiceTest {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Autowired
    private lateinit var service: IPromptService

    private val prompt = "What is the capital of France?"

    @MockitoBean // prevent autoconf of embedding vector store, not needed in this test
    private lateinit var vectorStore: VectorStore

    @MockitoBean("chatMemoryVectorStore") // prevent autoconf of memory vector store, not needed in this test
    private lateinit var chatMemoryVectorStore: VectorStore

    @Test
    fun `prompting LLM should return a string response`() {
        val response = service.prompt(PromptRequest(prompt))
        logger.debug("response: {}", response)
        assertThat(response.prompt).isEqualTo(prompt)
        assertThat(response.response).isNotBlank
    }

    @Test
    fun `asking LLM to generate a list of songs should return a valid wrapper object`() {
        val response = service.songs(2006)
        logger.debug("songs: {}", response)
        assertThat(response).isInstanceOf(SongResponse::class.java)
        assertThat(response.response)
            .isNotEmpty
            .allSatisfy {
                assertThat(it).isInstanceOf(Song::class.java)
                assertThat(it.title).isNotBlank()
            }
    }

    @Test
    fun `asking LLM to generate a list of movies should return a flux of string`() {
        StepVerifier
            .create(service.movies(2013).collectList())
            .assertNext {
                assertThat(it.joinToString("")).isNotBlank()
                logger.debug("movies: {}", it.joinToString(", "))
            }.verifyComplete()
    }
}

@Disabled
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.ai.ollama.base-url=http://localhost:11111",
        "spring.ai.ollama.init.pull-model-strategy=never",
    ],
)
@EnableWireMock(value = [ConfigureWireMock(baseUrlProperties = ["spring.ai.ollama.base-url"])])
class PromptServiceFakeTest {
    @Autowired
    private lateinit var service: IPromptService

    @MockitoBean // prevent autoconf of embedding vector store, not needed in this test
    private lateinit var vectorStore: VectorStore

    @MockitoBean("chatMemoryVectorStore") // prevent autoconf of memory vector store, not needed in this test
    private lateinit var chatMemoryVectorStore: VectorStore

    @BeforeEach
    fun beforeEach() {
        WireMock.stubFor(
            WireMock
                .post("/api/chat")
                .withHeader(HttpHeaders.ACCEPT, WireMock.equalTo(MediaType.APPLICATION_JSON_VALUE))
                .willReturn(
                    WireMock
                        .aResponse()
                        .withBodyFile("ollama_chat_response.json")
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
                ),
        )
    }

    @Test
    fun `fake prompt`() {
        val response = service.prompt(PromptRequest("How are you ?"))
        assertThat(response.response).isEqualTo("I'm functioning perfectly and ready to assist you!")
    }
}
