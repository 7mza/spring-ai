package com.hamza.springai.evaluation

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

@Tag(name = "evaluation", description = "LLM response evaluation")
@RequestMapping(value = ["/api/eval"], produces = [MediaType.APPLICATION_JSON_VALUE])
interface IEvalApi {
    @PostMapping
    @Operation(
        summary = "Evaluate & score prompt/response relevancy using LLM",
        description = """
Return a `JSON object response` from LLM with automatic parsing.<br /><br />
Retry mechanism for parsing errors.<br /><br />
(Local LLMs are not accurate).
""",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "OK",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = EvalResponse::class),
                        examples = [
                            ExampleObject(
                                name = "example-0",
                                description = "",
                                value = """
{
  "prompt": "What is the capital of France?",
  "response": "The capital of France is Paris.",
  "evaluation": { "pass": true, "score": 0.9, "feedback": "correct" }
}
""",
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    fun eval(
        @RequestBody @Valid request: EvalRequest,
    ): EvalResponse
}
