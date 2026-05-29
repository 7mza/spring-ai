package com.hamza.springai.mcp

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux

interface IMcpService {
    fun prompt(request: McpRequest): Flux<String>
}

@Service
class McpService(
    private val chatClient: ChatClient,
    private val provider: ToolCallbackProvider,
) : IMcpService {
    override fun prompt(request: McpRequest): Flux<String> =
        chatClient
            .mutate()
            .defaultTools { it.callbacks(provider) }
            .build()
            .prompt()
            .user(request.prompt)
            .stream()
            .content()
}
