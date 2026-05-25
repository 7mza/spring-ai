package com.hamza.springai.mcp

import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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
) : IWeatherTools {
    private val client = builder.baseUrl("https://wttr.in").build()

    @Tool(name = "getWeatherAt", description = "Get current weather for a location")
    override fun getWeatherAt(
        @ToolParam(description = "Name of the location, can be a city or a country")
        location: String,
    ): String =
        client
            .get()
            .uri("/{city}?format=3", location)
            .retrieve()
            .body<String>()
            ?: "No weather data for $location"
}

@Configuration
class Configs {
    @Bean
    fun toolCallbackProvider(tools: IWeatherTools): ToolCallbackProvider =
        MethodToolCallbackProvider.builder().toolObjects(tools).build()
}
