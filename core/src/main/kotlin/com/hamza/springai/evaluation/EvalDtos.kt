package com.hamza.springai.evaluation

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import org.springframework.ai.evaluation.EvaluationRequest
import org.springframework.ai.evaluation.EvaluationResponse

data class EvalRequest(
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

data class EvalResponse(
    val prompt: String,
    val response: String,
    val evaluation: Evaluation,
)

data class Evaluation(
    val pass: Boolean,
    val score: Float,
    val feedback: String,
)

fun EvaluationResponse.toEvalResponse(
    prompt: String,
    response: String,
): EvalResponse =
    EvalResponse(
        prompt = prompt,
        response = response,
        evaluation =
            Evaluation(
                pass = this.isPass,
                score = this.score,
                feedback = this.feedback,
            ),
    )
