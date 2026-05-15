package com.hamza.springai

import com.hamza.springai.prompt.PromptRequest
import com.hamza.springai.prompt.PromptResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

val promptEvaluationFailMessage =
    """
    answer "%s"
    is not relevant to
    prompt "%s"
    """.trimIndent()

data class PromptResponseNotRelevantException(
    private val prompt: PromptRequest,
    private val response: PromptResponse,
) : RuntimeException(promptEvaluationFailMessage.format(prompt.prompt, response.response!!))
