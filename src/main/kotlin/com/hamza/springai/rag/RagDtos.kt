package com.hamza.springai.rag

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class RagRequest(
    @field:NotBlank
    @field:Schema(
        description = "prompt to send to LLM",
        example = "Who is Gretchen Faulhauser? (short answer)",
    )
    val prompt: String,
)

internal class RagDtos
