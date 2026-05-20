package com.hamza.springai.rag.pipeline

import com.hamza.springai.rag.file.File
import com.hamza.springai.rag.file.IFileService
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.document.Document
import org.springframework.ai.model.transformer.KeywordMetadataEnricher
import org.springframework.ai.model.transformer.SummaryMetadataEnricher
import org.springframework.ai.reader.tika.TikaDocumentReader
import org.springframework.ai.transformer.splitter.TokenTextSplitter
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cloud.function.context.FunctionCatalog
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.InputStreamResource
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.integration.file.FileHeaders
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import org.springframework.scheduling.annotation.Scheduled
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

/**
 * Spring Cloud Function pipeline for ingesting documents from MinIO into Qdrant.
 *
 * Pipeline stages (wired via spring.cloud.function.definition):
 *   customS3Supplier → duplicationFilter → documentReader → documentSplitter
 *       → vectorStoreWriter → s3Archiver
 *
 * File lifecycle in S3:
 *   root/file.pdf  →  processing/file.pdf  →  processed/file.pdf
 *                                          ↘  error/file.pdf  (on any stage failure)
 *
 * The processing/ prefix acts as both a distributed lock (prevents concurrent poll cycles
 * from double-processing the same file) and a write-ahead log (files stuck there after a
 * crash can be manually moved back to root for retry).
 *
 * Deduplication strategy:
 *   S3 ETags (MD5 for single-part uploads) are used as content hashes. Already-ingested
 *   hashes are stored in H2. Files that pass the ETag check are moved to processing/ before
 *   any download begins — this is the point of no return for a given poll cycle.
 *
 * OOM prevention:
 *   Files are streamed from S3 directly to Tika via InputStreamResource — no byte[] buffering.
 *   The original approach (spring-s3-supplier) downloaded files to a local disk cache and loaded
 *   them entirely as byte[] message payloads, causing OOM on large PDFs.
 *
 * Concurrency model:
 *   S3AsyncClient handles listing, copy, and delete non-blocking via Mono.fromFuture.
 *   S3Client (blocking) is used only for getObject because Tika requires an InputStream —
 *   there is no reactive document parser with equivalent format coverage.
 *   All blocking I/O (Tika, Qdrant HTTP, Ollama HTTP, H2 JDBC) runs on a virtual thread
 *   scheduler instead of boundedElastic() — virtual threads are cheap and don't block OS threads.
 *   Concurrency is capped at 2 via flatMap(..., 2) at every stage to bound memory and
 *   limit concurrent LLM/Qdrant calls.
 *
 * Known limitations / planned work:
 *   - H2 is in-memory and resets on restart (outbox pattern planned for persistent deduplication)
 *   - Qdrant and H2 writes are not atomic (outbox pattern will fix this)
 *   - Files stuck in processing/ after a crash require manual recovery (move back to root)
 *   - Enrichers (language, quality, keyword, summary) are wired as beans but not yet
 *     added to the pipeline definition — add them to spring.cloud.function.definition when ready
 *   - Single-part ETags only — multipart ETags are not pure MD5 and would cause hash mismatches
 *
 * Quality enricher chunk-drop loop risk (fix needed before wiring qualityEnricher):
 *   qualityEnricher filters chunks below a quality score threshold. If ALL chunks of a file
 *   score below the threshold, the enricher returns an empty List<Document>. vectorStoreWriter
 *   detects this, skips Qdrant and H2, and archives the file to processed/ — so the file does
 *   not loop. HOWEVER: because H2 is in-memory, on restart the hash is gone. On the next poll,
 *   the file is back in processed/ so delimiter("/") hides it from the listing — no loop.
 *   BUT if someone manually moves the file back to root for a retry, it will go through the
 *   full pipeline again and get re-archived to processed/ with no H2 entry again.
 *   The deeper issue: a file with no quality chunks is silently "processed" with zero vectors
 *   in Qdrant. There is no distinction between "successfully indexed" and "indexed but empty".
 *   Fix to implement before wiring qualityEnricher:
 *     - Move zero-chunk files to a dedicated quarantine/ prefix instead of processed/
 *     - OR record a sentinel H2 entry (hash + name + status=EMPTY) to prevent silent re-processing
 *     - OR raise the alert level and require manual intervention for zero-chunk files
 */
@Configuration
@ConditionalOnProperty(name = ["spring.cloud.aws.s3.enabled"], havingValue = "true")
class Functions(
    private val vectorStore: VectorStore,
    private val fileService: IFileService,
    private val transformers: ITransformers,
    private val chatModel: ChatModel,
    /** Blocking S3 client — used only for getObject (Tika needs InputStream). */
    private val s3Client: S3Client,
    /** Non-blocking S3 client — used for all metadata ops (list, copy, delete). */
    private val s3AsyncClient: S3AsyncClient,
    @Value($$"${custom.supplier.remote-dir}") private val bucket: String,
    @Value($$"${custom.supplier.filename-regex}") private val filenameRegex: String,
    /**
     * Injected from spring.cloud.function.definition so pollS3() and the YAML definition
     * stay in sync — a mismatch causes a clear error at poll time rather than a silent no-op.
     */
    @Value($$"${spring.cloud.function.definition}") private val functionDefinition: String,
    private val catalog: FunctionCatalog,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Virtual thread scheduler for all blocking operations.
     * Chosen over boundedElastic() because spring.threads.virtual.enabled=true is active —
     * virtual threads handle blocking I/O without pinning OS threads, making the explicit
     * scheduler hop cheaper and the thread pool effectively unbounded without the overhead.
     */
    private val vtScheduler: Scheduler = Schedulers.fromExecutorService(Executors.newVirtualThreadPerTaskExecutor())

    /** Compiled once at construction — avoids recompiling the regex on every S3 listing. */
    private val filenamePattern = filenameRegex.toRegex()

    /**
     * Guards against overlapping poll cycles.
     * catalog.lookup<Runnable>().run() returns immediately — the reactive pipeline runs
     * asynchronously. Without this guard, every @Scheduled tick would start a new pipeline
     * run regardless of whether the previous one finished, causing double-ingestion races.
     * Reset by s3Archiver.doFinally (normal completion) or pipelineWatchdog (stuck detection).
     */
    private val pipelineRunning = AtomicBoolean(false)

    /** Timestamp of the last pipeline start — used by pipelineWatchdog to detect hangs. */
    private val pipelineStartedAt = AtomicLong(0)

    /**
     * Maximum pipeline duration before the watchdog force-resets pipelineRunning.
     * Files in processing/ at that point require manual recovery (move back to root).
     * Configurable via custom.supplier.pipeline-timeout (ms). Default: 1 hour.
     */
    @Value($$"${custom.supplier.pipeline-timeout}")
    private val pipelineTimeoutMs: Long = 3_600_000

    @PreDestroy
    fun destroy() = vtScheduler.dispose()

    /**
     * Triggers the ingestion pipeline on a fixed-delay schedule.
     * fixedDelay (not fixedRate) means the next tick starts N ms after pollS3() returns —
     * but since run() is async, this does NOT wait for the pipeline to finish. The
     * pipelineRunning guard is what enforces single-cycle execution.
     */
    @Scheduled(fixedDelayString = $$"${custom.supplier.polling-interval}")
    fun pollS3() {
        if (!pipelineRunning.compareAndSet(false, true)) {
            logger.debug("poll skipped — pipeline still running")
            return
        }
        pipelineStartedAt.set(System.currentTimeMillis())
        try {
            val pipeline =
                catalog.lookup<Runnable>(functionDefinition)
                    ?: error("pipeline definition not found in catalog: $functionDefinition")
            pipeline.run()
        } catch (e: Exception) {
            logger.error("pipeline failed to start", e)
            pipelineRunning.set(false)
        }
    }

    /**
     * Safety net for stuck pipelines.
     * If a file hangs in Tika parsing or a downstream service becomes unresponsive,
     * the pipeline never completes and pipelineRunning stays true indefinitely, blocking
     * all future poll cycles. The watchdog detects this and force-resets the flag.
     * Files in processing/ at that point remain there for manual recovery.
     */
    @Scheduled(fixedDelay = 60_000)
    fun pipelineWatchdog() {
        if (!pipelineRunning.get()) return
        val elapsed = System.currentTimeMillis() - pipelineStartedAt.get()
        if (elapsed > pipelineTimeoutMs) {
            logger.error(
                "pipeline stuck for {}ms (timeout: {}ms) — force-resetting, files may remain in processing/",
                elapsed,
                pipelineTimeoutMs,
            )
            pipelineRunning.set(false)
        }
    }

    /**
     * Lists root-level S3 objects and emits one message per file.
     *
     * Why S3AsyncClient + listObjectsV2Paginator:
     *   - Non-blocking — no scheduler hop needed for listing
     *   - Paginator transparently handles buckets with >1000 objects
     *
     * Why delimiter("/"):
     *   S3 has no real folders — keys are just strings. delimiter("/") tells S3 to group
     *   keys containing "/" into commonPrefixes (invisible to contents()). This means
     *   processing/, processed/, and error/ objects never appear in the listing, so the
     *   pipeline can't accidentally re-pick files it already moved.
     *
     * Message headers set here:
     *   - FileHeaders.FILENAME: original root key (e.g. "report.pdf") — preserved through
     *     all stages even after the payload key changes to "processing/report.pdf"
     *   - file_hash: S3 ETag stripped of surrounding quotes (AWS wraps ETags in quotes)
     *     Used as the content hash for deduplication. Valid for single-part uploads only
     *     (multipart ETags are not pure MD5).
     *
     * Filter order: regex first (cheap string match), then size check (avoids warning noise
     * for files that don't match the pattern anyway).
     */
    @Bean
    fun customS3Supplier(): Supplier<Flux<Message<String>>> =
        Supplier {
            Flux
                .from(s3AsyncClient.listObjectsV2Paginator { it.bucket(bucket).delimiter("/") })
                .flatMapIterable { it.contents() }
                .filter { it.key().matches(filenamePattern) }
                .filter {
                    val empty = it.size() == 0L
                    if (empty) logger.warn("file: {} is empty, skipping", it.key())
                    !empty
                }.map {
                    MessageBuilder
                        .withPayload(it.key())
                        .setHeader(FileHeaders.FILENAME, it.key())
                        .setHeader("file_hash", it.eTag().replace("\"", ""))
                        .build()
                }
        }

    /**
     * Deduplication gate. Checks H2 for the file's ETag hash.
     *
     * Already-ingested files:
     *   Moved directly from root to processed/ and dropped from the pipeline.
     *   This handles the case where a file was re-uploaded to the bucket root after
     *   a previous successful ingestion (same content = same ETag = already in H2).
     *
     * New files:
     *   Moved from root to processing/ BEFORE any download begins.
     *   This is the distributed-lock step — once moved, no subsequent poll cycle will
     *   see this file in the root listing (delimiter("/") hides processing/).
     *   The message payload is updated to "processing/$fileName" so downstream stages
     *   fetch from the correct key. FileHeaders.FILENAME retains the original base name.
     *
     * Concurrency=2: bounds concurrent H2 reads and S3 move operations.
     *
     * Known race: with concurrency=2, two messages with the same ETag (identical files
     * uploaded under different names) can both pass existsByHash simultaneously. Both
     * proceed through the pipeline; vectorStoreWriter handles the resulting H2 constraint
     * violation gracefully.
     */
    @Bean
    fun duplicationFilter(): Function<Flux<Message<String>>, Flux<Message<String>>> =
        Function { flux ->
            flux.flatMap({ message ->
                val fileName = message.headers[FileHeaders.FILENAME]?.toString() ?: "unknown"
                val hash = message.headers["file_hash"]?.toString() ?: return@flatMap Mono.empty()
                Mono
                    .fromCallable { fileService.existsByHash(hash) }
                    .subscribeOn(vtScheduler)
                    .flatMap { exists ->
                        if (exists) {
                            logger.warn("file: {} already ingested, archiving", fileName)
                            moveAsync(fileName, "processed/$fileName")
                                .onErrorResume { ex ->
                                    logger.error("failed to archive duplicate: {}", fileName, ex)
                                    Mono.empty()
                                }.then(Mono.empty())
                        } else {
                            logger.info("ingesting new file: {}", fileName)
                            moveAsync(fileName, "processing/$fileName")
                                .thenReturn(
                                    MessageBuilder
                                        .withPayload("processing/$fileName")
                                        .copyHeadersIfAbsent(message.headers)
                                        .build(),
                                )
                        }
                    }
            }, 2)
        }

    /**
     * Downloads from S3 and parses with Tika. Emits one List<Document> per file.
     *
     * Why S3Client (blocking) instead of S3AsyncClient here:
     *   Tika requires a synchronous InputStream. S3AsyncClient.getObject() with a reactive
     *   publisher would require bridging back to a blocking InputStream, adding complexity
     *   with no benefit. The blocking call runs on vtScheduler (virtual threads), so it
     *   does not pin OS threads or block the Reactor event loop.
     *
     * Why InputStreamResource instead of byte[]:
     *   The original pipeline (spring-s3-supplier) loaded files as byte[] message payloads,
     *   causing OOM on large PDFs. InputStreamResource streams bytes directly from the S3
     *   ResponseInputStream to Tika's parser — the binary content is never fully buffered.
     *   Note: Tika/PDFBox still buffers the PDF structure internally for random access during
     *   parsing (required by the PDF spec). True zero-copy is not achievable for PDFs.
     *
     * Why List<Document> (not Flux<Document>):
     *   Tika may return multiple Document objects for one file (e.g., one per attachment in
     *   an email). Keeping them as one list preserves the 1-file-to-1-pipeline-emission
     *   cardinality. Exploding with flatMapMany would cause vectorStoreWriter and s3Archiver
     *   to run N times per file, hitting H2 unique constraint violations and issuing N
     *   redundant S3 archive calls.
     *
     * Concurrency=2: limits simultaneous S3 downloads + Tika parse sessions. Bounds peak
     *   memory to approximately 2 × (file size as parsed text) at any point in the pipeline.
     */
    @Bean
    fun documentReader(): Function<Flux<Message<String>>, Flux<List<Document>>> =
        Function { flux ->
            flux.flatMap({ message ->
                val key = message.payload
                val baseName = message.headers[FileHeaders.FILENAME]?.toString() ?: key
                Mono
                    .fromCallable {
                        val hash = message.headers["file_hash"]?.toString() ?: error("missing file_hash for $baseName")
                        s3Client.getObject { it.bucket(bucket).key(key) }.use { stream ->
                            TikaDocumentReader(InputStreamResource(stream))
                                .get()
                                .also { if (it.isEmpty()) error("Tika returned no documents for $baseName") }
                                .map {
                                    it
                                        .mutate()
                                        .metadata("file_hash", hash)
                                        .metadata("file_name", baseName)
                                        .build()
                                }
                        }
                    }.subscribeOn(vtScheduler)
                    .onErrorResume { ex ->
                        logger.error("failed to read: {}, moving to error/", baseName, ex)
                        moveAsync(key, "error/$baseName").then(Mono.empty())
                    }
            }, 2)
        }

    /**
     * Splits all Tika sections of one file into a single combined chunk list.
     *
     * Why one TokenTextSplitter per Tika section (not shared):
     *   TokenTextSplitter thread safety is not guaranteed. With concurrency=2, two files
     *   could be split simultaneously. A shared splitter instance could race on internal
     *   state. Building per-section is cheap (pure configuration object) and safe.
     *
     * Why flatMap across tikaDocuments (not a single splitter over all):
     *   Each Tika Document represents a logical section (e.g., a page, an attachment).
     *   Splitting them independently preserves section boundaries in the chunk metadata
     *   and avoids the splitter merging text across unrelated sections.
     *   All resulting chunks are collected into one List<Document> so the downstream
     *   pipeline still sees exactly one emission per file.
     */
    @Bean
    fun documentSplitter(): Function<Flux<List<Document>>, Flux<List<Document>>> =
        Function { flux ->
            flux.flatMap({ tikaDocuments ->
                val name = tikaDocuments.first().metadata["file_name"]?.toString() ?: "unknown"
                Mono
                    .fromCallable {
                        tikaDocuments.flatMap { TokenTextSplitter.builder().build().apply(listOf(it)) }
                    }.subscribeOn(vtScheduler)
                    .onErrorResume { ex ->
                        logger.error("failed to split: {}, moving to error/", name, ex)
                        moveAsync("processing/$name", "error/$name").then(Mono.empty())
                    }
            }, 2)
        }

    /**
     * Writes chunks to Qdrant and records the file hash in H2.
     *
     * Empty chunk list handling:
     *   An empty list occurs when qualityEnricher filters ALL chunks below the quality threshold.
     *   Without this check, the file name is never emitted, s3Archiver never runs, and the file
     *   stays in processing/ indefinitely. The pipelineWatchdog would eventually force-reset
     *   pipelineRunning, but the file would still be stranded in processing/ and require manual
     *   recovery — on every restart (H2 wipe), it would re-enter the pipeline and loop.
     *   Current fix: archive to processed/ with a WARN log — the file was evaluated but produced
     *   no indexable content. No H2 entry is written, so if moved back to root it re-processes.
     *   See class KDoc "Quality enricher chunk-drop loop risk" for the full analysis and
     *   the fix that needs to be implemented before wiring qualityEnricher.
     *
     * Write order (Qdrant first, H2 second):
     *   If H2 save fails after a successful Qdrant write, the file stays in processing/
     *   and gets re-processed on the next run. The second Qdrant write produces duplicate
     *   vectors. This is the known non-atomicity trade-off. The outbox pattern will fix it.
     *
     * DataIntegrityViolationException on fileService.save:
     *   Can occur in a same-ETag race: two files with identical content entering the pipeline
     *   in the same poll cycle both pass existsByHash (both false before either saves).
     *   The second save hits the unique constraint on hash. We catch it specifically,
     *   log at WARN, and still emit the filename so s3Archiver archives both files.
     *   The second file's vectors are in Qdrant but its name is not in H2 — acceptable
     *   given the rarity of same-content duplicates in a managed bucket.
     *
     * Concurrency=2: matches documentReader's cap. Limits simultaneous Qdrant write sessions.
     */
    @Bean
    fun vectorStoreWriter(): Function<Flux<List<Document>>, Flux<String>> =
        Function { flux ->
            flux
                .flatMap({ docs ->
                    val first = docs.firstOrNull()
                    val name = first?.metadata?.get("file_name")?.toString() ?: "unknown"
                    val hash = first?.metadata?.get("file_hash")?.toString() ?: "unknown"
                    if (first == null) {
                        logger.warn("no chunks produced for: {}, moving to processed/", name)
                        return@flatMap moveAsync("processing/$name", "processed/$name").then(Mono.empty())
                    }
                    Mono
                        .fromCallable {
                            logger.info("writing {} documents to vector store", docs.size)
                            vectorStore.accept(docs)
                            try {
                                fileService.save(File(hash = hash, name = name))
                            } catch (_: DataIntegrityViolationException) {
                                // same-ETag race — see KDoc above
                                logger.warn("duplicate content race for: {}, archiving anyway", name)
                            }
                            logger.info("{} documents written to vector store", docs.size)
                            name
                        }.subscribeOn(vtScheduler)
                        .onErrorResume { ex ->
                            logger.error("failed to write: {}, moving to error/", name, ex)
                            moveAsync("processing/$name", "error/$name").then(Mono.empty())
                        }
                }, 2)
        }

    /**
     * Terminal stage — moves successfully processed files from processing/ to processed/.
     *
     * Per-element onErrorResume:
     *   A single failed archive does not terminate the flux. The file is logged and left
     *   in processing/ for manual recovery. Remaining files in the batch are unaffected.
     *
     * doFinally:
     *   Resets pipelineRunning regardless of how the flux terminates (complete, error, cancel).
     *   This is the primary reset path. pipelineWatchdog is the fallback for stuck pipelines.
     *
     * Why subscribe() is called explicitly inside the Consumer:
     *   Spring Cloud Function calls Consumer.accept(flux) and expects the consumer to own
     *   the subscription. The framework does not subscribe for reactive Consumers — the
     *   explicit subscribe() here is required. doFinally on this subscription is what drives
     *   the pipelineRunning reset.
     */
    @Bean
    fun s3Archiver(): Consumer<Flux<String>> =
        Consumer { flux ->
            flux
                .flatMap { name ->
                    moveAsync("processing/$name", "processed/$name")
                        .onErrorResume { ex ->
                            logger.error("failed to archive: {}, leaving in processing/", name, ex)
                            Mono.empty()
                        }
                }.doFinally { pipelineRunning.set(false) }
                .subscribe(null) { logger.error("s3 archiver terminated with error", it) }
        }

    /**
     * Moves an S3 object from sourceKey to destinationKey via server-side copy + delete.
     * The copy is a server-side operation — no bytes flow through the application.
     *
     * Idempotency:
     *   NoSuchKeyException on copyObject is silently skipped (source already gone).
     *   Both the direct exception and its CompletionException-wrapped form are handled
     *   because Mono.fromFuture propagates CompletableFuture failures as-is.
     *   The delete only runs if copyObject emitted a result (flatMap skips on empty),
     *   so a skipped copy never triggers a spurious delete.
     *
     * Retry note:
     *   If the delete fails transiently after a successful copy, moveAsync returns an error.
     *   On the next poll cycle, the source key no longer exists (copy succeeded), so the
     *   retry's copyObject throws NoSuchKeyException, which is silently skipped, and the
     *   whole move is treated as a no-op. Net result: idempotent end-to-end.
     */
    private fun moveAsync(
        sourceKey: String,
        destinationKey: String,
    ): Mono<Void> =
        Mono
            .fromFuture {
                s3AsyncClient.copyObject {
                    it
                        .sourceBucket(bucket)
                        .sourceKey(sourceKey)
                        .destinationBucket(bucket)
                        .destinationKey(destinationKey)
                }
            }.onErrorResume { ex ->
                val isNotFound = ex is NoSuchKeyException || ex.cause is NoSuchKeyException
                if (isNotFound) {
                    logger.warn("source key not found, skipping move: {} → {}", sourceKey, destinationKey)
                    Mono.empty()
                } else {
                    Mono.error(ex)
                }
            }.flatMap { Mono.fromFuture { s3AsyncClient.deleteObject { it.bucket(bucket).key(sourceKey) } } }
            .doOnSuccess { logger.info("moved: {} → {}", sourceKey, destinationKey) }
            .then()

    /**
     * Enrichers — NOT yet wired into spring.cloud.function.definition.
     * Add them to the definition string when ready to enable enrichment.
     *
     * BEFORE wiring qualityEnricher, read the "Quality enricher chunk-drop loop risk" section
     * in the class KDoc and implement the fix — otherwise files that score below the quality
     * threshold will be silently archived with zero vectors and no H2 record.
     *
     * Why transformers are constructed per-call inside fromCallable (not as shared singletons):
     *   KeywordMetadataEnricher and SummaryMetadataEnricher make LLM calls and may hold
     *   mutable state (request builders, response accumulators). With concurrency=2, two
     *   chunks could be enriched simultaneously. Constructing per-call is cheap (stateless
     *   config + chatModel reference) and eliminates the concurrency risk entirely.
     *
     * Concurrency=2 on enrichers:
     *   Each call blocks on an Ollama HTTP round-trip. The Ollama model is configured with
     *   OLLAMA_NUM_PARALLEL=4, so allowing more than 2-4 concurrent enricher calls would
     *   saturate the model without improving throughput.
     */
    @Bean
    fun languageEnricher(): Function<Flux<List<Document>>, Flux<List<Document>>> =
        Function { flux ->
            flux.flatMap({ Mono.fromCallable { transformers.languageEnricher().apply(it) }.subscribeOn(vtScheduler) }, 2)
        }

    @Bean
    fun qualityEnricher(): Function<Flux<List<Document>>, Flux<List<Document>>> =
        Function { flux ->
            flux.flatMap({ Mono.fromCallable { transformers.qualityEvaluator().apply(it) }.subscribeOn(vtScheduler) }, 2)
        }

    @Bean
    fun keywordEnricher(): Function<Flux<List<Document>>, Flux<List<Document>>> =
        Function { flux ->
            flux.flatMap(
                { Mono.fromCallable { KeywordMetadataEnricher(chatModel, 5).apply(it) }.subscribeOn(vtScheduler) },
                2,
            )
        }

    @Bean
    fun summaryEnricher(): Function<Flux<List<Document>>, Flux<List<Document>>> =
        Function { flux ->
            flux.flatMap({
                Mono
                    .fromCallable {
                        SummaryMetadataEnricher(chatModel, listOf(SummaryMetadataEnricher.SummaryType.CURRENT)).apply(it)
                    }.subscribeOn(vtScheduler)
            }, 2)
        }
}
