package com.hamza.springai.prompt

import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.wiremock.spring.ConfigureWireMock
import org.wiremock.spring.EnableWireMock

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.ai.ollama.base-url=http://localhost:11111",
        "spring.ai.ollama.init.pull-model-strategy=never",
    ],
)
@EnableWireMock(value = [ConfigureWireMock(baseUrlProperties = ["spring.ai.ollama.base-url"])])
class PromptServiceTest {
    @Autowired
    private lateinit var service: IPromptService

    @MockitoBean // prevent autoconf of embedding vector store, not needed in this test
    private lateinit var vectorStore: VectorStore

    @MockitoBean
    @Qualifier("chatMemoryVectorStore") // prevent autoconf of memory vector store, not needed in this test
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
