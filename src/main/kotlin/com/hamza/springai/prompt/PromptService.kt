package com.hamza.springai.prompt

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.responseEntity
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.retry.support.RetrySynchronizationManager
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import tools.jackson.core.JacksonException

interface IPromptService {
    fun prompt(
        request: PromptRequest,
        context: String = "",
    ): PromptResponse

    fun songs(year: Int): SongResponse

    fun movies(year: Int): Flux<String>
}

@Service
class PromptService(
    chatClientBuilder: ChatClient.Builder,
    @Value("classpath:/prompts/topic_prompt.st") private val topicsTemplate: Resource,
) : IPromptService {
    private val logger = LoggerFactory.getLogger(javaClass)

    /* modify chat options (model, temp, ...etc.) globally through yml or locally here
     * local > global
     */
    private val chatOptionsBuilder: ChatOptions.Builder<*> = ChatOptions.builder()

    private val chatClient =
        chatClientBuilder
            // .defaultOptions(chatOptionsBuilder)
            .build()

    private val promptTemplate =
        """
        prompt: {prompt}
        context: {context}
        """.trimIndent()

    override fun prompt(
        request: PromptRequest,
        context: String,
    ): PromptResponse =
        chatClient
            .prompt()
            .user { it.text(promptTemplate).param("prompt", request.prompt).param("context", context) }
            .call()
            .content()
            ?.let { PromptResponse(prompt = request.prompt, response = it) }
            ?: error("LLM response was null")

    @Retryable(retryFor = [JacksonException::class], maxAttempts = 5)
    override fun songs(year: Int): SongResponse {
        val attempt = RetrySynchronizationManager.getContext()?.retryCount ?: 0
        if (attempt > 0) logger.warn("LLM response parsing failed, retry attempt {}", attempt)
        return chatClient
            .prompt()
            .user { it.text(topicsTemplate).param("topic", "song").param("year", year) }
            .call()
            .responseEntity<SongResponse>()
            // .entity(object : ParameterizedTypeReference<List<String>>() {})!! if no wrapper
            .also { entity ->
                entity.response()?.metadata?.usage?.let {
                    logger.info(
                        "token usage: prompt={}, generation={}, total={}",
                        it.promptTokens,
                        it.completionTokens,
                        it.totalTokens,
                    )
                }
            }.entity()!!
    }

    override fun movies(year: Int): Flux<String> =
        chatClient
            .prompt()
            .user { it.text(topicsTemplate).param("topic", "movie").param("year", year) }
            .stream()
            .content()

    @Recover
    fun songs(
        ex: JacksonException,
        year: Int,
    ): SongResponse {
        logger.warn("all LLM response parsing failed, applying recovery")
        return SongResponse(listOf(Song("Could not parse LLM response")))
    }
}
