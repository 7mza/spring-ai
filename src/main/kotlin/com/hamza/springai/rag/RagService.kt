package com.hamza.springai.rag

import com.hamza.springai.prompt.PromptResponse
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer
import org.springframework.ai.rag.preretrieval.query.transformation.TranslationQueryTransformer
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicReference

interface IRagService {
    fun pullContext(prompt: String): String

    fun promptWithManualRag(request: RagRequest): PromptResponse

    fun promptWithQAAdvisor(request: RagRequest): PromptResponse

    fun promptWithModularAdvisor(request: RagRequest): PromptResponse

    fun promptWithExpanding(request: RagRequest): PromptResponse
}

@Service
class RagService(
    private val chatClientBuilder: ChatClient.Builder,
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

    override fun promptWithQAAdvisor(request: RagRequest): PromptResponse =
        chatClient
            .mutate()
            .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore).build())
            .build()
            .prompt()
            .user(request.prompt)
            // OR: .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
            .advisors {
                // SQL WHERE on document metadata, useful with enrichers
                // it.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, "language == 'en'")
            }.call()
            .content()
            ?.let { PromptResponse(prompt = request.prompt, response = it) }
            ?: error("LLM response was null")

    override fun promptWithModularAdvisor(request: RagRequest): PromptResponse {
        val enhancedQuery = AtomicReference<String>()
        return chatClient
            .mutate()
            .defaultAdvisors(
                RetrievalAugmentationAdvisor
                    .builder()
                    // you can create your own `org.springframework.ai.rag.retrieval.search.DocumentRetriever`
                    .documentRetriever(VectorStoreDocumentRetriever.builder().vectorStore(vectorStore).build())
                    .queryTransformers(
                        // use LLM to translate query
                        TranslationQueryTransformer
                            .builder()
                            .chatClientBuilder(chatClientBuilder)
                            .targetLanguage("English")
                            .build(),
                        // use LLM to rewrite query to be more concise before similarity search
                        RewriteQueryTransformer.builder().chatClientBuilder(chatClientBuilder).build(),
                        // capture final enhanced query
                        { query ->
                            enhancedQuery.set(query.text())
                            logger.info("enhanced query: {}", query.text())
                            query
                        },
                    ).build(),
            ).build()
            .prompt()
            .user(request.prompt)
            .advisors {
                // it.param(VectorStoreDocumentRetriever.FILTER_EXPRESSION, "language == 'en'")
            }.call()
            .content()
            ?.let { PromptResponse(prompt = request.prompt, enhancedPrompt = enhancedQuery.get(), response = it) }
            ?: error("LLM response was null")
    }

    override fun promptWithExpanding(request: RagRequest): PromptResponse {
        val expandedQueries = AtomicReference<String>()
        val expander =
            MultiQueryExpander
                .builder()
                .chatClientBuilder(chatClientBuilder)
                .numberOfQueries(4)
                .includeOriginal(false)
                .build()
        return chatClient
            .mutate()
            .defaultAdvisors(
                RetrievalAugmentationAdvisor
                    .builder()
                    .documentRetriever(VectorStoreDocumentRetriever.builder().vectorStore(vectorStore).build())
                    .queryExpander { query ->
                        val expanded = expander.expand(query)
                        val joined = expanded.joinToString(" | ") { it.text() }
                        expandedQueries.set(joined)
                        logger.info("expanded queries: {}", joined)
                        expanded
                    }.build(),
            ).build()
            .prompt()
            .user(request.prompt)
            .call()
            .content()
            ?.let { PromptResponse(prompt = request.prompt, enhancedPrompt = expandedQueries.get(), response = it) }
            ?: error("LLM response was null")
    }

    private fun debugDocumentsScore(prompt: String) {
        vectorStore
            .similaritySearch(
                SearchRequest
                    .builder()
                    .query(prompt)
                    .similarityThresholdAll()
                    .build(),
            ).forEach { logger.info("file={}    score={}", it.metadata["file_name"], it.score) }
    }
}
