package com.hamza.springai.mcp

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.serverError
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.wiremock.spring.ConfigureWireMock
import org.wiremock.spring.EnableWireMock
import reactor.test.StepVerifier

@SpringBootTest
@EnableWireMock(value = [ConfigureWireMock(baseUrlProperties = ["custom.currency.api"])])
class CurrencyToolsTest {
    @Autowired
    private lateinit var currencyTools: ICurrencyTools

    @Test
    fun getExchangeRate() {
        stubFor(
            get(urlPathEqualTo("/EUR/MAD"))
                .willReturn(
                    aResponse()
                        .withBody(
                            """{ "date": "2026-05-25", "base": "EUR", "quote": "MAD", "rate": 10.7 }""".trimIndent(),
                        ).withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
                ),
        )
        StepVerifier
            .create(currencyTools.getExchangeRate("EUR", "MAD"))
            .assertNext {
                assertThat(it.date).isEqualTo("2026-05-25")
                assertThat(it.base).isEqualTo("EUR")
                assertThat(it.quote).isEqualTo("MAD")
                assertThat(it.rate).isEqualTo(10.7)
            }.verifyComplete()
    }

    @Test
    fun getExchangeRateError() {
        stubFor(get(urlPathEqualTo("/MAD/EUR")).willReturn(serverError()))
        StepVerifier
            .create(currencyTools.getExchangeRate("MAD", "EUR"))
            .expectError()
            .verify()
    }
}
