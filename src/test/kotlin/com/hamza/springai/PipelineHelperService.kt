package com.hamza.springai

import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestComponent
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.util.DigestUtils
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.NoSuchKeyException

interface IPipelineHelperService {
    fun collectResources(): List<Resource>

    fun collectFileNameHashPairs(): Map<String, String>

    fun collectDocumentChunks(name: String): List<Document>

    fun initBucket(bucket: String)

    fun existsInS3(
        bucket: String,
        key: String,
    ): Boolean
}

@TestComponent
class PipelineHelperService(
    private val vectorStore: VectorStore,
    private val s3Client: S3Client,
    @Value($$"${custom.supplier.filename-regex}") private val filenameRegex: String,
) : IPipelineHelperService {
    override fun collectResources(): List<Resource> =
        PathMatchingResourcePatternResolver()
            .getResources("classpath:docs/*")
            .filter { it.filename?.matches(filenameRegex.toRegex()) == true && it.contentAsByteArray.isNotEmpty() }

    override fun collectFileNameHashPairs(): Map<String, String> =
        collectResources().associate { r -> r.filename!! to DigestUtils.md5DigestAsHex(r.contentAsByteArray) }

    override fun collectDocumentChunks(name: String) =
        vectorStore.similaritySearch(
            SearchRequest
                .builder()
                .query("a")
                .similarityThresholdAll()
                // .similarityThreshold(1.0) // [0,1], higher = most similar, more selective
                .topK(100)
                // .filterExpression("file_name == '$name'")
                .filterExpression(FilterExpressionBuilder().eq("file_name", name).build())
                .build(),
        )

    override fun existsInS3(
        bucket: String,
        key: String,
    ): Boolean =
        try {
            s3Client.headObject { it.bucket(bucket).key(key) }
            true
        } catch (e: NoSuchKeyException) {
            false
        }

    override fun initBucket(bucket: String) {
        if (s3Client.listBuckets().buckets().none { it.name() == bucket }) {
            s3Client.createBucket { it.bucket(bucket) }
        }
        collectResources()
            .forEach { resource ->
                val bytes = resource.contentAsByteArray
                s3Client.putObject(
                    { req -> req.bucket(bucket).key(resource.filename!!) },
                    RequestBody.fromBytes(bytes),
                )
            }
    }
}
