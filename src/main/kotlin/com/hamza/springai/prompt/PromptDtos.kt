package com.hamza.springai.prompt

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

data class PromptRequest(
    @field:NotBlank
    @field:NotEmpty
    @field:Schema(
        description = "prompt to send",
        defaultValue = "How are you doing ?",
    )
    val prompt: String,
)

data class PromptResponse(
    val response: String?,
)
