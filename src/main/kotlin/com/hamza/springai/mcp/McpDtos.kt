package com.hamza.springai.mcp

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class McpRequest(
    @field:NotBlank
    @field:Schema(
        description = "prompt to send to LLM",
        example = """
Can you list the content of /projects ? Don't recurse, just return the names of folders and files at first level one by one.
""",
    )
    val prompt: String,
)

internal class McpDtos
