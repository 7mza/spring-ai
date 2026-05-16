package com.hamza.springai

data class NotRelevantException(
    val prompt: String,
    val response: String,
) : RuntimeException(
        """
        answer "%s"
        is not relevant to
        prompt "%s"
        """.trimIndent().format(response, prompt),
    )

internal class Toto
