package com.hamza.springai.prompt

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.evaluation.EvaluationRequest
import org.springframework.ai.evaluation.EvaluationResponse
import org.springframework.ai.evaluation.Evaluator
import org.springframework.stereotype.Component

@Component
class ScoreEvaluator(
    private val chatClientBuilder: ChatClient.Builder,
) : Evaluator {
    private data class ParsingIntermediary(
        val score: Float = 0f,
        val feedback: String = "",
    )

    override fun evaluate(request: EvaluationRequest): EvaluationResponse =
        chatClientBuilder
            .build()
            .prompt()
            .system(
                """
                You are an evaluation engine. You must always respond with a single raw JSON object and nothing else.
                No markdown, no code blocks, no explanation — only valid JSON in exactly this format:
                {"score": <float between 0.0 and 1.0>, "feedback": "<your reasoning>"}
                Wrong answer examples :
                - {{"score": 10}
                - {"feedback": "reasons"}}
                - {"feedback: "reasons}}
                - {{"score": , "feedback": }}
                """.trimIndent(),
            ).user {
                it
                    .text(
                        """
                        Evaluate if the response correctly answers the query.
                        Score from 0.0 (completely wrong) to 1.0 (perfectly correct).
                        Query: {query}
                        Response: {response}
                        """.trimIndent(),
                    ).param("query", request.userText)
                    .param("response", request.responseContent)
            }.call()
            .entity(ParsingIntermediary::class.java)!!
            .let {
                EvaluationResponse(it.score >= 0.5f, it.score, it.feedback, emptyMap())
            }
}
