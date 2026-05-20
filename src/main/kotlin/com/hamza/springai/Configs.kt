package com.hamza.springai

import io.awspring.cloud.autoconfigure.core.AwsClientBuilderConfigurer
import io.awspring.cloud.autoconfigure.core.AwsConnectionDetails
import io.awspring.cloud.autoconfigure.s3.properties.S3Properties
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.slf4j.LoggerFactory
import org.springframework.ai.model.ollama.autoconfigure.OllamaConnectionDetails
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.ansi.AnsiColor
import org.springframework.boot.ansi.AnsiOutput
import org.springframework.boot.ansi.AnsiStyle
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.info.BuildProperties
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.boot.restclient.RestClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.retry.annotation.EnableRetry
import org.springframework.web.client.RestClient
import org.zalando.logbook.spring.LogbookClientHttpRequestInterceptor
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException
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
        val message = "api running at $address/swagger-ui"
        logger.info(AnsiOutput.toString(AnsiColor.BRIGHT_GREEN, AnsiStyle.BOLD, message))
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

    // increase ollama client timeout
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

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = ["spring.cloud.aws.s3.enabled"], havingValue = "true")
    fun s3AsyncClient(
        configurer: AwsClientBuilderConfigurer,
        connectionDetails: ObjectProvider<AwsConnectionDetails>,
        properties: S3Properties,
    ): S3AsyncClient =
        configurer
            .configureAsyncClient(S3AsyncClient.builder(), properties, connectionDetails.ifAvailable, null, null)
            .serviceConfiguration(properties.toS3Configuration())
            .build()

    @Bean
    @ConditionalOnProperty(name = ["spring.cloud.aws.s3.enabled"], havingValue = "true")
    fun createBucket(
        s3Client: S3Client,
        @Value($$"${custom.supplier.remote-dir}") bucket: String,
    ) = ApplicationRunner {
        runCatching { s3Client.createBucket { it.bucket(bucket) } }
            .onFailure { if (it !is BucketAlreadyOwnedByYouException) throw it }
    }
}

// mirror jpa create-drop: wipe vector store on shutdown
@Configuration
@ConditionalOnProperty(name = ["spring.jpa.hibernate.ddl-auto"], havingValue = "create-drop")
class QdrantConfigs(
    private val restClientBuilder: RestClient.Builder,
    @Value($$"${spring.ai.vectorstore.qdrant.host:localhost}") private val host: String,
    @Value($$"${spring.ai.vectorstore.qdrant.collection-name}") private val name: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @EventListener(ContextClosedEvent::class)
    fun wipeStore() {
        runCatching {
            restClientBuilder
                .baseUrl("http://$host:6333")
                .build()
                .delete()
                .uri("/collections/{name}", name)
                .retrieve()
                .toBodilessEntity()
            logger.info("wiped qdrant collection '{}'", name)
        }.onFailure { logger.warn("error wiping qdrant collection '{}': {}", name, it.message) }
    }
}
