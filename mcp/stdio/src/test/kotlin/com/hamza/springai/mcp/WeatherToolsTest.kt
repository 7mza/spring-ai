package com.hamza.springai.mcp

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.noContent
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.wiremock.spring.ConfigureWireMock
import org.wiremock.spring.EnableWireMock

@SpringBootTest
@EnableWireMock(value = [ConfigureWireMock(baseUrlProperties = ["custom.weather.api"])])
class WeatherToolsTest {
    @Autowired
    private lateinit var weatherTools: IWeatherTools

    @Test
    fun getWeatherAt() {
        stubFor(
            get(urlPathEqualTo("/rabat"))
                .withQueryParam("format", equalTo("3"))
                .willReturn(ok("amal")),
        )
        val result = weatherTools.getWeatherAt("rabat")
        assertThat(result).isEqualTo("amal")
    }

    @Test
    fun getWeatherAtEmpty() {
        stubFor(
            get(urlPathEqualTo("/temara"))
                .withQueryParam("format", equalTo("3"))
                .willReturn(noContent()),
        )
        val result = weatherTools.getWeatherAt("temara")
        assertThat(result).isEqualTo("No weather data for temara")
    }
}
