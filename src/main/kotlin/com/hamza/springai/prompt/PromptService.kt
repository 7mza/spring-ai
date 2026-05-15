package com.hamza.springai.prompt

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.evaluation.EvaluationRequest
import org.springframework.ai.evaluation.Evaluator
import org.springframework.stereotype.Service

interface IPromptService {
    fun prompt(request: PromptRequest): PromptResponse

    fun evaluate(request: EvaluateRequest): EvaluateResponse
}

@Service
class PromptService(
    private val chatClientBuilder: ChatClient.Builder,
    private val evaluator: Evaluator,
) : IPromptService {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun prompt(request: PromptRequest): PromptResponse =
        chatClientBuilder
            .build()
            .prompt()
            .user(request.prompt)
            .call()
            .content()
            .let { PromptResponse(it) }

    override fun evaluate(request: EvaluateRequest): EvaluateResponse =
        evaluator
            .evaluate(EvaluationRequest(request.prompt, request.response))
            .let { EvaluateResponse(pass = it.isPass, score = it.score, feedback = it.feedback) }
}
