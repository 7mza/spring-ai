package com.hamza.springai.rag

import com.hamza.springai.prompt.PromptResponse
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Service

interface IRagService {
    fun pullContext(prompt: String): String

    fun promptWithManualRag(request: RagRequest): PromptResponse

    fun promptWithAdvisor(request: RagRequest): PromptResponse
}

@Service
class RagService(
    chatClientBuilder: ChatClient.Builder,
    private val vectorStore: VectorStore,
) : IRagService {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val chatClient = chatClientBuilder.build()

    private val promptTemplate =
        """
        prompt: {prompt}
        context: {context}
        """.trimIndent()

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

    override fun promptWithManualRag(request: RagRequest): PromptResponse =
        chatClient
            .prompt()
            .user {
                it
                    .text(promptTemplate)
                    .param("prompt", request.prompt)
                    .param("context", pullContext(request.prompt))
            }.call()
            .content()
            ?.let { PromptResponse(prompt = request.prompt, response = it) }
            ?: error("LLM response was null")

    override fun promptWithAdvisor(request: RagRequest): PromptResponse =
        chatClient
            .mutate()
            .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore).build())
            .build()
            .prompt()
            .user(request.prompt)
            // .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
            .advisors {
                // SQL WHERE on document metadata, useful with enrichers
                // it.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, "language == 'en'")
            }.call()
            .content()
            ?.let { PromptResponse(prompt = request.prompt, response = it) }
            ?: error("LLM response was null")

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
