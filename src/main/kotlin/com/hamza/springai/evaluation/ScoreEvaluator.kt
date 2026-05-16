package com.hamza.springai.evaluation

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.evaluation.EvaluationRequest
import org.springframework.ai.evaluation.EvaluationResponse
import org.springframework.ai.evaluation.Evaluator
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.retry.support.RetrySynchronizationManager
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException

@Component
class ScoreEvaluator(
    private val chatClientBuilder: ChatClient.Builder,
    @Value("classpath:/prompts/eval_system.st") private val system: Resource,
    @Value("classpath:/prompts/eval_prompt.st") private val prompt: Resource,
) : Evaluator {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Retryable(retryFor = [JacksonException::class], maxAttempts = 5)
    override fun evaluate(request: EvaluationRequest): EvaluationResponse {
        val attempt = RetrySynchronizationManager.getContext()?.retryCount ?: 0
        if (attempt > 0) logger.debug("LLM response parsing failed, retry attempt {}", attempt)
        return chatClientBuilder
            .build()
            .prompt()
            .system(system)
            .user {
                it
                    .text(prompt)
                    .param("query", request.userText)
                    .param("response", request.responseContent)
            }.call()
            .entity(ParsingIntermediary::class.java)!! // will forward the format as JSON in user prompt
            .let {
                EvaluationResponse(it.score >= 0.5f, it.score, it.feedback, emptyMap())
            }
    }

    @Recover
    fun evaluate(
        ex: JacksonException,
        request: EvaluationRequest,
    ): EvaluationResponse {
        logger.debug("all LLM response parsing failed, applying recovery")
        return EvaluationResponse(false, 0f, "Could not parse LLM response", emptyMap())
    }

    private data class ParsingIntermediary(
        val score: Float = 0f,
        val feedback: String = "",
    )
}
