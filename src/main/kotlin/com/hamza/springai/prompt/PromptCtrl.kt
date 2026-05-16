package com.hamza.springai.prompt

import org.springframework.web.bind.annotation.RestController

@RestController
class PromptCtrl(
    private val service: IPromptService,
) : IPromptApi {
    override fun prompt(
        request: PromptRequest,
        evaluate: Boolean?,
    ): PromptResponse = service.prompt(request, evaluate)

    override fun evaluate(request: EvaluateRequest): PromptResponse = service.evaluate(request)
}
