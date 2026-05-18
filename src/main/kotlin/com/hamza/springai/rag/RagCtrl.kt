package com.hamza.springai.rag

import com.hamza.springai.prompt.PromptResponse
import org.springframework.web.bind.annotation.RestController

@RestController
class RagCtrl(
    private val service: IRagService,
) : IRagApi {
    override fun prompt(request: RagRequest): PromptResponse = service.prompt(request)
}
