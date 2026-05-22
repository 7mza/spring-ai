package com.hamza.springai.memory

import com.hamza.springai.prompt.PromptResponse
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor
import org.springframework.ai.chat.client.advisor.vectorstore.VectorStoreChatMemoryAdvisor
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

interface IMemoryService {
    fun promptWithJdbcMemory(
        conversationId: String,
        request: MemoryRequest,
    ): PromptResponse

    fun promptWithVectorStoreMemory(
        conversationId: String,
        request: MemoryRequest,
    ): PromptResponse
}

@Service
class MemoryService(
    chatClientBuilder: ChatClient.Builder,
    private val chatMemory: ChatMemory,
    @Qualifier("chatMemoryVectorStore") private val vectorStore: VectorStore,
) : IMemoryService {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val chatClient =
        chatClientBuilder
            .build()

    override fun promptWithJdbcMemory(
        conversationId: String,
        request: MemoryRequest,
    ): PromptResponse =
        chatClient
            .prompt()
            .user(request.prompt)
            .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            .advisors { it.param(ChatMemory.CONVERSATION_ID, conversationId) }
            .call()
            .content()
            ?.let { PromptResponse(prompt = request.prompt, response = it) }
            ?: error("LLM response was null")

    override fun promptWithVectorStoreMemory(
        conversationId: String,
        request: MemoryRequest,
    ): PromptResponse =
        chatClient
            .prompt()
            .user(request.prompt)
            .advisors(VectorStoreChatMemoryAdvisor.builder(vectorStore).build())
            .advisors { it.param(ChatMemory.CONVERSATION_ID, conversationId) }
            .call()
            .content()
            ?.let { PromptResponse(prompt = request.prompt, response = it) }
            ?: error("LLM response was null")
}
