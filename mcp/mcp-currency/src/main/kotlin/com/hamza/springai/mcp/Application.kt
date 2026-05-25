package com.hamza.springai.mcp

import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

@SpringBootApplication
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

data class RateResponse(
    val date: String,
    val base: String,
    val quote: String,
    val rate: Double,
)

interface ICurrencyTools {
    fun getExchangeRate(
        base: String,
        quote: String,
    ): Mono<RateResponse>
}

@Service
class CurrencyTools(
    builder: WebClient.Builder,
    @Value($$"${custom.currency.api}") api: String,
) : ICurrencyTools {
    private val client = builder.baseUrl(api).build()

    @McpTool(name = "getExchangeRate", description = "Get current exchange rate between two currencies")
    override fun getExchangeRate(
        @McpToolParam(description = "Base currency code, e.g. USD") base: String,
        @McpToolParam(description = "Target currency code, e.g. EUR") quote: String,
    ): Mono<RateResponse> =
        client
            .get()
            .uri("/{base}/{quote}", base, quote)
            .retrieve()
            .bodyToMono<RateResponse>()
}
