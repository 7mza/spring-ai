package com.hamza.springai.prompt

import com.hamza.springai.PromptResponseNotRelevantException
import org.hibernate.validator.internal.util.Contracts
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.evaluation.RelevancyEvaluator
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.evaluation.EvaluationRequest
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.retry.support.RetrySynchronizationManager
import org.springframework.stereotype.Service

const val PROMPT_EVALUATION_RECOVERY_MESSAGE = "I'm sorry, I wasn't able to answer the question."

interface IPromptService {
    fun prompt(
        request: PromptRequest,
        evaluate: Boolean = false,
    ): PromptResponse
}

@Service
class PromptService(
    chatClientBuilder: ChatClient.Builder,
) : IPromptService {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val chatOptionsBuilder: ChatOptions.Builder<*> = ChatOptions.builder()
    private val chatClientBuilder: ChatClient =
        chatClientBuilder
            .defaultOptions(chatOptionsBuilder)
            .build()
    private val evaluator: RelevancyEvaluator =
        RelevancyEvaluator
            .builder()
            .chatClientBuilder(chatClientBuilder)
            .build()

    @Retryable(retryFor = [PromptResponseNotRelevantException::class], maxAttempts = 5)
    override fun prompt(
        request: PromptRequest,
        evaluate: Boolean,
    ): PromptResponse {
        val attempt = (RetrySynchronizationManager.getContext()?.retryCount ?: 0) + 1
        logger.debug("response evaluation failed, retry attempt {}", attempt)
        return chatClientBuilder
            .prompt()
            .user(request.prompt)
            .call()
            .content()
            .let {
                if (evaluate) {
                    evaluate(request, PromptResponse(it))
                } else {
                    PromptResponse(it)
                }
            }
    }

    private fun evaluate(
        prompt: PromptRequest,
        response: PromptResponse,
    ): PromptResponse {
        Contracts.assertNotNull(response.response, "response must not be null")
        val evaluation = evaluator.evaluate(EvaluationRequest(prompt.prompt, response.response!!))
        return if (evaluation.isPass.not()) {
            throw PromptResponseNotRelevantException(prompt, response)
        } else {
            response
        }
    }

    @Recover
    fun recover(ex: PromptResponseNotRelevantException): PromptResponse {
        logger.debug("all @Retry failed, applying @Recover")
        return PromptResponse(PROMPT_EVALUATION_RECOVERY_MESSAGE)
    }
}
