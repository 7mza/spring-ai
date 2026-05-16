package com.hamza.springai.prompt

import com.hamza.springai.NotRelevantException
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.evaluation.Evaluator
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.retry.support.RetrySynchronizationManager
import org.springframework.stereotype.Service

interface IPromptService {
    fun prompt(
        request: PromptRequest,
        evaluate: Boolean = false,
    ): PromptResponse

    fun evaluate(request: EvaluateRequest): PromptResponse
}

@Service
class PromptService(
    private val chatClientBuilder: ChatClient.Builder,
    private val evaluator: Evaluator,
) : IPromptService {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Retryable(retryFor = [NotRelevantException::class], maxAttempts = 5)
    override fun prompt(
        request: PromptRequest,
        evaluate: Boolean,
    ): PromptResponse {
        val attempt = RetrySynchronizationManager.getContext()?.retryCount ?: 0
        if (attempt > 0) logger.debug("response evaluation failed, retry attempt {}", attempt)
        return chatClientBuilder
            .build()
            .prompt()
            .user(request.prompt)
            .call()
            .content()
            .let {
                val content = checkNotNull(it) { "Chat response content was null" }
                var evaluation: PromptResponse? = null
                if (evaluate) {
                    evaluation = evaluate(EvaluateRequest(request.prompt, content))
                    if ((evaluation.evaluation?.pass ?: false).not()) throw NotRelevantException(request.prompt, content)
                }
                PromptResponse(
                    prompt = request.prompt,
                    response = content,
                    evaluation = evaluation?.evaluation,
                )
            }
    }

    override fun evaluate(request: EvaluateRequest): PromptResponse =
        evaluator
            .evaluate(request.toEvaluationRequest())
            .let {
                PromptResponse(
                    prompt = request.prompt,
                    response = request.response,
                    evaluation = it.toEvaluateResponse(),
                )
            }

    @Recover
    fun recover(ex: NotRelevantException): PromptResponse {
        logger.debug("all evaluation retries failed, applying recovery")
        return PromptResponse(
            prompt = ex.prompt,
            response = ex.response,
            evaluation =
                EvaluateResponse(
                    pass = false,
                    score = 0f,
                    feedback = "evaluation failed",
                ),
        )
    }
}
