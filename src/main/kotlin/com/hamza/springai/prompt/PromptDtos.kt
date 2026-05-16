package com.hamza.springai.prompt

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import org.springframework.ai.evaluation.EvaluationRequest
import org.springframework.ai.evaluation.EvaluationResponse

data class PromptRequest(
    @field:NotBlank
    @field:Schema(
        description = "prompt to send",
        example = "What is the capital of France?",
    )
    val prompt: String,
)

data class PromptResponse(
    val prompt: String,
    val response: String?,
    val evaluation: EvaluateResponse?,
)

data class EvaluateRequest(
    @field:NotBlank
    @field:Schema(
        description = "prompt to evaluate",
        example = "What is the capital of France?",
    )
    val prompt: String,
    @field:NotBlank
    @field:Schema(
        description = "response to evaluate",
        example = "The capital of France is Paris",
    )
    val response: String,
) {
    fun toEvaluationRequest(): EvaluationRequest = EvaluationRequest(this.prompt, this.response)
}

data class EvaluateResponse(
    val pass: Boolean,
    val score: Float,
    val feedback: String,
)

fun EvaluationResponse.toEvaluateResponse(): EvaluateResponse =
    EvaluateResponse(
        pass = this.isPass,
        score = this.score,
        feedback = this.feedback,
    )
