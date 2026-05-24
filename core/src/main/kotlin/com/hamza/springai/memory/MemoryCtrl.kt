package com.hamza.springai.memory

import com.hamza.springai.prompt.PromptResponse
import org.springframework.web.bind.annotation.RestController

@RestController
class MemoryCtrl(
    private val service: IMemoryService,
) : IMemoryApi {
    override fun promptWithJdbcMemory(
        conversationId: String,
        request: MemoryRequest,
    ): PromptResponse = service.promptWithJdbcMemory(conversationId, request)

    override fun promptWithVectorStoreMemory(
        conversationId: String,
        request: MemoryRequest,
    ): PromptResponse = service.promptWithVectorStoreMemory(conversationId, request)
}
