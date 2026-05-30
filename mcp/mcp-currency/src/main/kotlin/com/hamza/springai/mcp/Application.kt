package com.hamza.springai.mcp

import io.micrometer.observation.ObservationPredicate
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

@SpringBootApplication
class Application {
    @Bean // don't pollute tracer
    fun observationPredicate(tracingProperties: TracingProperties?): ObservationPredicate =
        ObservationPredicate { _, context ->
            (context as? ServerRequestObservationContext)
                ?.carrier
                ?.uri
                ?.path
                ?.let { path -> tracingProperties?.excludeUris?.none { path.startsWith(it.trim()) } }
                ?: true
        }
}

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

@Configuration
@ConfigurationProperties(prefix = "tracing")
class TracingProperties {
    var excludeUris: List<String>? = null
}
