package com.hamza.springai.memory

import org.springframework.web.bind.annotation.RestController

@RestController
class MemoryCtrl(
    private val service: IMemoryService,
) : IMemoryApi {
    override fun promptWithJdbcMemory(
        conversationId: String,
        request: MemoryRequest,
    ): MemoryResponse = service.promptWithJdbcMemory(conversationId, request)

    override fun promptWithVectorStoreMemory(
        conversationId: String,
        request: MemoryRequest,
    ): MemoryResponse = service.promptWithVectorStoreMemory(conversationId, request)
}
