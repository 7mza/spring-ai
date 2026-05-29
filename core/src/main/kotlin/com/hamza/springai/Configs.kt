package com.hamza.springai

import io.awspring.cloud.autoconfigure.core.AwsClientBuilderConfigurer
import io.awspring.cloud.autoconfigure.core.AwsConnectionDetails
import io.awspring.cloud.autoconfigure.s3.properties.S3Properties
import io.micrometer.observation.ObservationPredicate
import io.qdrant.client.QdrantClient
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.h2.tools.Server
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.memory.ChatMemoryRepository
import org.springframework.ai.chat.memory.MessageWindowChatMemory
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.document.Document
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse
import org.springframework.ai.model.ollama.autoconfigure.OllamaConnectionDetails
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.filter.Filter
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.ansi.AnsiColor
import org.springframework.boot.ansi.AnsiOutput
import org.springframework.boot.ansi.AnsiStyle
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.info.BuildProperties
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.boot.restclient.RestClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.http.server.observation.ServerRequestObservationContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.retry.annotation.EnableRetry
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.client.RestClient
import org.zalando.logbook.spring.LogbookClientHttpRequestInterceptor
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException
import java.net.InetAddress
import java.time.Duration

@Configuration
@EnableRetry
@EnableScheduling
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

    /* to use external DB tool (IntelliJ DB viewer for example) to connect to H2
     * url: jdbc:h2:tcp://localhost:PORT/~/DB_NAME */
    @Profile("h2-tcp")
    @Bean(initMethod = "start", destroyMethod = "stop")
    fun h2TcpServer(
        @Value($$"${custom.h2.port}") port: String,
    ): Server {
        logger.info("launching h2 in tcp mode")
        return Server.createTcpServer(
            "-tcp",
            "-tcpAllowOthers",
            "-tcpPort",
            port,
        )
    }

    // chat memory window configs
    @Bean
    fun chatMemory(chatMemoryRepository: ChatMemoryRepository): ChatMemory =
        MessageWindowChatMemory
            .builder()
            .chatMemoryRepository(chatMemoryRepository)
            .maxMessages(50)
            .build()

    // separate collection for chat memory
    @Bean(name = ["chatMemoryVectorStore"], defaultCandidate = false)
    @Profile("!openapi-plugin")
    fun chatMemoryVectorStore(
        client: QdrantClient,
        embeddingModel: EmbeddingModel,
        @Value($$"${custom.memory.store}") name: String,
    ): VectorStore =
        QdrantVectorStore
            .builder(client, embeddingModel)
            .collectionName(name)
            .initializeSchema(true)
            .build()

    @Bean
    fun chatClient(chatClientBuilder: ChatClient.Builder): ChatClient = chatClientBuilder.build()

    // don't send crap to tracer
    @Bean
    fun observationPredicate(tracingProperties: TracingProperties?): ObservationPredicate =
        ObservationPredicate { name, context ->
            if (name.startsWith("task")) return@ObservationPredicate false // polling
            (context as? ServerRequestObservationContext)
                ?.carrier
                ?.requestURI
                ?.let { uri -> tracingProperties?.excludeUris?.none { uri.startsWith(it.trim()) } }
                ?: true
        }
}

@Configuration
@ConfigurationProperties(prefix = "tracing")
class TracingProperties {
    var excludeUris: List<String>? = null
}

// mirror jpa create-drop: wipe vector/memory stores on shutdown
@Configuration
@ConditionalOnProperty(name = ["spring.jpa.hibernate.ddl-auto"], havingValue = "create-drop")
class CleaningConfigs(
    private val restClientBuilder: RestClient.Builder,
    @Value($$"${spring.ai.vectorstore.qdrant.host}") private val host: String,
    @Value($$"${spring.ai.vectorstore.qdrant.collection-name}") private val embeddingStoreName: String,
    @Value($$"${custom.memory.store}") private val chatMemoryStore: String,
    private val jdbcTemplate: JdbcTemplate,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @EventListener(ContextClosedEvent::class)
    fun clean() {
        wipeStore(embeddingStoreName)
        wipeStore(chatMemoryStore)
        runCatching {
            jdbcTemplate.execute("delete from SPRING_AI_CHAT_MEMORY")
            logger.trace("wiped table '{}'", "SPRING_AI_CHAT_MEMORY")
        }.onFailure { logger.trace("error wiping table 'SPRING_AI_CHAT_MEMORY': {}", it.message) }
    }

    private fun wipeStore(name: String) {
        runCatching {
            restClientBuilder
                .baseUrl("http://$host:6333")
                .build()
                .delete()
                .uri("/collections/{name}", name)
                .retrieve()
                .toBodilessEntity()
            logger.trace("wiped qdrant collection '{}'", name)
        }.onFailure { logger.trace("error wiping qdrant collection '{}': {}", name, it.message) }
    }
}

@Configuration // FIXME: ugly
@Profile("openapi-plugin")
class OpenapiPluginConfigs {
    @Bean
    fun noOpChatModel(): ChatModel = ChatModel { throw NotImplementedError() }

    @Bean
    fun noOpEmbeddingModel(): EmbeddingModel =
        object : EmbeddingModel {
            override fun call(request: EmbeddingRequest): EmbeddingResponse = throw NotImplementedError()

            override fun embed(document: Document): FloatArray = throw NotImplementedError()
        }

    @Bean(name = ["chatMemoryVectorStore"], defaultCandidate = false)
    fun noOpVectorStore(): VectorStore =
        object : VectorStore {
            override fun add(documents: List<Document>): Unit = throw NotImplementedError()

            override fun delete(idList: List<String>): Unit = throw NotImplementedError()

            override fun delete(filterExpression: Filter.Expression): Unit = throw NotImplementedError()

            override fun similaritySearch(request: SearchRequest): List<Document> = throw NotImplementedError()
        }
}
