package com.hamza.springai.rag

import com.hamza.springai.prompt.IPromptService
import com.hamza.springai.prompt.PromptRequest
import com.hamza.springai.prompt.PromptResponse
import org.slf4j.LoggerFactory
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Service

interface IRagService {
    fun pullContext(prompt: String): String

    fun prompt(request: RagRequest): PromptResponse
}

@Service
class RagService(
    private val service: IPromptService,
    private val vectorStore: VectorStore,
) : IRagService {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun prompt(request: RagRequest): PromptResponse =
        pullContext(request.prompt).let { service.prompt(PromptRequest(request.prompt), it) }

    override fun pullContext(prompt: String): String {
        debugDocumentsScore(prompt)
        val documents =
            vectorStore.similaritySearch(
                SearchRequest
                    .builder()
                    .query(prompt)
                    .similarityThreshold(0.3) // use debug score to have a general idea
                    .build(),
            )
        val files = documents.map { it.metadata["file_name"] }.distinct()
        if (files.isNotEmpty()) {
            logger.info("pulled context from {} files: {} for prompt: {}", files.size, files, prompt)
        } else {
            logger.warn("no context pulled for prompt: {}", prompt)
        }
        return documents.joinToString(System.lineSeparator()) { it.text.orEmpty() }
    }

    private fun debugDocumentsScore(prompt: String) {
        vectorStore
            .similaritySearch(
                SearchRequest
                    .builder()
                    .query(prompt)
                    .similarityThresholdAll()
                    .build(),
            ).forEach { logger.debug("file={}    score={}", it.metadata["file_name"], it.score) }
    }
}
