package com.hamza.springai.memory

import com.hamza.springai.prompt.PromptResponse
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
    private val chatClient: ChatClient,
    private val chatMemory: ChatMemory,
    @Qualifier("chatMemoryVectorStore") private val vectorStore: VectorStore,
) : IMemoryService {
    override fun promptWithJdbcMemory(
        conversationId: String,
        request: MemoryRequest,
    ): PromptResponse =
        chatClient
            .prompt()
            .user(request.prompt)
            .advisors {
                it
                    .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                    .param(ChatMemory.CONVERSATION_ID, conversationId)
            }.call()
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
            .advisors {
                it
                    .advisors(VectorStoreChatMemoryAdvisor.builder(vectorStore).build())
                    .param(ChatMemory.CONVERSATION_ID, conversationId)
            }.call()
            .content()
            ?.let { PromptResponse(prompt = request.prompt, response = it) }
            ?: error("LLM response was null")
}
