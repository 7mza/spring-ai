package com.hamza.springai.prompt

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.retry.support.RetrySynchronizationManager
import org.springframework.stereotype.Service
import tools.jackson.core.JacksonException

interface IPromptService {
    fun prompt(request: PromptRequest): PromptResponse

    fun songs(year: Int): SongResponse
}

@Service
class PromptService(
    private val chatClientBuilder: ChatClient.Builder,
    @Value("classpath:/prompts/songs_prompt.st") private val prompt: Resource,
) : IPromptService {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /* modify chat options (model, temp, ...etc.) globally through yml or locally here
     * local > global
     */
    private val chatOptionsBuilder: ChatOptions.Builder<*> = ChatOptions.builder()

    override fun prompt(request: PromptRequest): PromptResponse =
        chatClientBuilder
            // .defaultOptions(chatOptionsBuilder)
            .build()
            .prompt()
            .user(request.prompt)
            .call()
            .content()
            .let {
                PromptResponse(
                    prompt = request.prompt,
                    response = checkNotNull(it) { "LLM response was null" },
                )
            }

    @Retryable(retryFor = [JacksonException::class], maxAttempts = 5)
    override fun songs(year: Int): SongResponse {
        val attempt = RetrySynchronizationManager.getContext()?.retryCount ?: 0
        if (attempt > 0) logger.debug("LLM response parsing failed, retry attempt {}", attempt)
        return chatClientBuilder
            .build()
            .prompt()
            .user {
                it
                    .text(prompt)
                    .param("year", year)
            }.call()
            .entity(SongResponse::class.java)!! // ParameterizedTypeReference<X> if no wrapper
    }

    @Recover
    fun songs(
        ex: JacksonException,
        year: Int,
    ): SongResponse {
        logger.debug("all LLM response parsing failed, applying recovery")
        return SongResponse(listOf(Song("Could not parse LLM response")))
    }
}
