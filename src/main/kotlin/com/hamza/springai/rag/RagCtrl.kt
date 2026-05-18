package com.hamza.springai.rag

import com.hamza.springai.prompt.PromptResponse
import org.springframework.web.bind.annotation.RestController

@RestController
class RagCtrl(
    private val service: IRagService,
) : IRagApi {
    override fun promptWithManualRag(request: RagRequest): PromptResponse = service.promptWithManualRag(request)

    override fun promptWithAdvisor(request: RagRequest): PromptResponse = service.promptWithAdvisor(request)
}
