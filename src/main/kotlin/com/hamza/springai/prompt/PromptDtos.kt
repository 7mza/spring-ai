package com.hamza.springai.prompt

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class PromptRequest(
    @field:NotBlank
    @field:Schema(
        description = "prompt to send to LLM",
        example = "What is the capital of France?",
    )
    val prompt: String,
)

data class PromptResponse(
    val prompt: String,
    val enhancedPrompt: String? = null,
    val response: String,
)

data class Song(
    val title: String,
)

data class SongResponse(
    val response: List<Song>,
)
