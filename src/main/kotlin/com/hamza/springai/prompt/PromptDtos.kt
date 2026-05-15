package com.hamza.springai.prompt

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

data class PromptRequest(
    @field:NotBlank
    @field:NotEmpty
    @field:Schema(
        description = "prompt to send",
        defaultValue = "What is the capital of France?",
    )
    val prompt: String,
)

data class PromptResponse(
    val response: String?,
)

data class EvaluateRequest(
    @field:NotBlank
    @field:NotEmpty
    @field:Schema(
        description = "prompt to evaluate",
        defaultValue = "What is the capital of France?",
    )
    val prompt: String,
    @field:NotBlank
    @field:NotEmpty
    @field:Schema(
        description = "response to evaluate",
        defaultValue = "The capital of France is Paris",
    )
    val response: String,
)

data class EvaluateResponse(
    val pass: Boolean,
    val score: Float,
    val feedback: String,
)
