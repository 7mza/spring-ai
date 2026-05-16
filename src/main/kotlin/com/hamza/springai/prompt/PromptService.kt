package com.hamza.springai.prompt

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.stereotype.Service

interface IPromptService {
    fun prompt(request: PromptRequest): PromptResponse
}

@Service
class PromptService(
    private val chatClientBuilder: ChatClient.Builder,
) : IPromptService {
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
}
