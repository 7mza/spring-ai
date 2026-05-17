package com.hamza.springai

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.slf4j.LoggerFactory
import org.springframework.ai.model.ollama.autoconfigure.OllamaConnectionDetails
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.info.BuildProperties
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.boot.restclient.RestClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.retry.annotation.EnableRetry
import org.springframework.web.client.RestClient
import org.zalando.logbook.spring.LogbookClientHttpRequestInterceptor
import java.net.InetAddress
import java.time.Duration

@Configuration
@EnableRetry
class Configs(
    @Value($$"${server.port}") private val port: Int,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun jacksonCustomizer(): JsonMapperBuilderCustomizer = JsonMapperBuilderCustomizer { it.findAndAddModules() }

    @EventListener(ApplicationReadyEvent::class)
    fun readyListener() {
        val address = "http://${InetAddress.getLocalHost().hostAddress}:$port"
        // logger.info("app running at {}", address)
        logger.info("api running at {}/swagger-ui", address)
    }

    @Bean
    fun openAPI(buildProperties: BuildProperties): OpenAPI =
        OpenAPI().info(
            Info()
                .title("${buildProperties.name} API")
                .version(buildProperties.version)
                .description("TODO"),
        )

    @Bean
    fun logbookCustomizer(interceptor: LogbookClientHttpRequestInterceptor): RestClientCustomizer =
        RestClientCustomizer { it.requestInterceptor(interceptor) }

    /* Increase ollama client timeout
     * FIXME: does it override other defaultChatOptions ?
     */
    @Bean
    fun ollamaApi(
        connectionDetails: OllamaConnectionDetails,
        interceptor: LogbookClientHttpRequestInterceptor,
    ): OllamaApi =
        OllamaApi
            .builder()
            .baseUrl(connectionDetails.baseUrl)
            .restClientBuilder(
                RestClient
                    .builder()
                    .requestFactory(
                        JdkClientHttpRequestFactory().apply {
                            setReadTimeout(Duration.ofMinutes(2))
                        },
                    ).requestInterceptor(interceptor),
            ).build()
}
