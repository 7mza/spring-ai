package com.hamza.springai

import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestComponent
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.util.DigestUtils

interface IPipelineHelperService {
    fun collectFiles(): Map<String, String>

    fun collectChunks(name: String): List<Document>
}

@TestComponent
class PipelineHelperService(
    private val vectorStore: VectorStore,
    @Value($$"${file.supplier.filename-regex}") private val filenameRegex: String,
) : IPipelineHelperService {
    override fun collectFiles(): Map<String, String> =
        PathMatchingResourcePatternResolver()
            .getResources("classpath:docs/*")
            .filter {
                it.filename?.matches(filenameRegex.toRegex()) == true &&
                    it.contentAsByteArray.isNotEmpty()
            }.associate { r -> r.filename!! to DigestUtils.md5DigestAsHex(r.contentAsByteArray) }

    override fun collectChunks(name: String) =
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
}
