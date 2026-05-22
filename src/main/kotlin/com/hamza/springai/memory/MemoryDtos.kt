package com.hamza.springai.memory

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class MemoryRequest(
    @field:NotBlank
    @field:Schema(
        description = "prompt to send to LLM",
        example = "Who is Gandalf?",
    )
    val prompt: String,
)

internal class MemoryDtos
