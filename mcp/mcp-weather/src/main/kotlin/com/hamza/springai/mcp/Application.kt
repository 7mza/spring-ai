package com.hamza.springai.mcp

import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@SpringBootApplication
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

interface IWeatherTools {
    fun getWeatherAt(location: String): String
}

@Service
class WeatherTools(
    builder: RestClient.Builder,
    @Value($$"${custom.weather.api}") api: String,
) : IWeatherTools {
    private val client = builder.baseUrl(api).build()

    @McpTool(name = "getWeatherAt", description = "Get current weather for a location")
    override fun getWeatherAt(
        @McpToolParam(description = "Name of the location, can be a city or a country") location: String,
    ): String =
        client
            .get()
            .uri("/{location}?format=3", location)
            .retrieve()
            .body<String>()
            ?: "No weather data for $location"
}
