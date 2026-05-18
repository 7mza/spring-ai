package com.hamza.springai.rag

import com.hamza.springai.rag.file.File
import com.hamza.springai.rag.file.IFileService
import org.slf4j.LoggerFactory
import org.springframework.ai.document.Document
import org.springframework.ai.reader.tika.TikaDocumentReader
import org.springframework.ai.transformer.splitter.TokenTextSplitter
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.cloud.function.context.FunctionCatalog
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ByteArrayResource
import org.springframework.integration.file.FileHeaders
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import org.springframework.util.DigestUtils
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.function.Consumer
import java.util.function.Function

@Configuration
class Functions {
    private val logger = LoggerFactory.getLogger(javaClass)

    // don't register anything unless specified in YAML
    @Bean
    @ConditionalOnExpression($$"!'${spring.cloud.function.definition:}'.isBlank()")
    fun launchFunctions(catalog: FunctionCatalog): ApplicationRunner =
        ApplicationRunner { catalog.lookup<Runnable>(null).run() }

    @Bean
    fun duplicationFilter(fileService: IFileService): Function<Flux<Message<ByteArray>>, Flux<Message<ByteArray>>> =
        Function { flux ->
            flux.flatMap { message ->
                val fileName = message.headers[FileHeaders.FILENAME]?.toString() ?: "unknown"
                val hash = DigestUtils.md5DigestAsHex(message.payload)
                Mono
                    .fromCallable { fileService.existsByHash(hash) }
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnNext { exists ->
                        if (exists) {
                            logger.info("file: {} already ingested, skipping", fileName)
                        } else {
                            logger.info("ingesting new file: {}", fileName)
                        }
                    }.filter { !it }
                    .map { MessageBuilder.fromMessage(message).setHeader("file_hash", hash).build() }
            }
        }

    @Bean
    fun documentReader(): Function<Flux<Message<ByteArray>>, Flux<Document>> =
        Function { flux ->
            flux
                .filter { it.payload.isNotEmpty() }
                .flatMap { message ->
                    Mono
                        .fromCallable {
                            val fileName = message.headers[FileHeaders.FILENAME]?.toString() ?: "unknown"
                            val payload = message.payload
                            val hash = message.headers["file_hash"]?.toString() ?: DigestUtils.md5DigestAsHex(payload)
                            val doc =
                                TikaDocumentReader(ByteArrayResource(payload))
                                    .get()
                                    .firstOrNull() ?: error("Tika returned no documents for $fileName")
                            doc
                                .mutate()
                                .metadata("file_hash", hash)
                                .metadata("file_name", fileName)
                                .build()
                        }.subscribeOn(Schedulers.boundedElastic())
                }
        }

    @Bean
    fun documentSplitter(): Function<Flux<Document>, Flux<List<Document>>> =
        TokenTextSplitter.builder().build().let { splitter ->
            Function { flux ->
                flux.flatMap { doc ->
                    Mono
                        .fromCallable { splitter.apply(listOf(doc)) }
                        .subscribeOn(Schedulers.parallel())
                }
            }
        }

    @Bean
    fun vectorStoreWriter(
        vectorStore: VectorStore,
        fileService: IFileService,
    ): Consumer<Flux<List<Document>>> =
        Consumer { flux ->
            flux
                .filter { it.isNotEmpty() }
                .flatMap { docs ->
                    Mono
                        .fromCallable {
                            val hash =
                                docs.first().metadata["file_hash"]?.toString() ?: error("missing file_hash metadata")
                            val name =
                                docs.first().metadata["file_name"]?.toString() ?: error("missing file_name metadata")
                            logger.info("writing {} documents to vector store", docs.size)
                            vectorStore.accept(docs)
                            fileService.save(File(hash = hash, name = name))
                            logger.info("{} documents written to vector store", docs.size)
                        }.subscribeOn(Schedulers.boundedElastic())
                }.subscribe(null) { logger.error("ingestion pipeline failed", it) }
        }
}
