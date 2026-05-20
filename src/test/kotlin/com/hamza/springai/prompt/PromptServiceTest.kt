package com.hamza.springai.prompt

import com.github.tomakehurst.wiremock.client.WireMock
import com.hamza.springai.TestcontainersConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.wiremock.spring.ConfigureWireMock
import org.wiremock.spring.EnableWireMock

@Disabled
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.ai.ollama.base-url=http://localhost:11111", "spring.ai.ollama.init.pull-model-strategy=never"],
)
@Import(TestcontainersConfig::class)
@EnableWireMock(value = [ConfigureWireMock(baseUrlProperties = ["spring.ai.ollama.base-url"])])
class PromptServiceTest {
    @Autowired
    private lateinit var service: IPromptService

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
