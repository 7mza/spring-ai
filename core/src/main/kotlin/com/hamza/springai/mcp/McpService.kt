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
    chatClientBuilder: ChatClient.Builder,
    tools: ToolCallbackProvider,
) : IMcpService {
    private val chatClient =
        chatClientBuilder
            .defaultToolCallbacks(tools) // FIXME: deprecation
            .build()

    override fun prompt(request: McpRequest): Flux<String> =
        chatClient
            .prompt()
            .user(request.prompt)
            .stream()
            .content()
}
