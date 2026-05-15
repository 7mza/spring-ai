package com.hamza.springai.prompt

import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.ai.evaluation.Evaluator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.wiremock.spring.ConfigureWireMock
import org.wiremock.spring.EnableWireMock

@Disabled
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.ai.ollama.init.pull-model-strategy=never"],
)
@EnableWireMock(value = [ConfigureWireMock(baseUrlProperties = ["spring.ai.ollama.base-url"])])
class PromptServiceTest {
    @Autowired
    private lateinit var service: IPromptService

    @MockitoBean
    private lateinit var evaluator: Evaluator

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
