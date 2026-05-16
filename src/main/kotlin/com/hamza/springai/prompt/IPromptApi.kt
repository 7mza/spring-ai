package com.hamza.springai.prompt

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
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
import org.springframework.web.bind.annotation.RequestParam

@Tag(name = "prompt", description = "")
@RequestMapping(value = ["/api/prompt"], produces = [MediaType.APPLICATION_JSON_VALUE])
interface IPromptApi {
    @PostMapping("/chat")
    @Operation(
        summary = "Send a chat prompt to configured LLM backend",
        description = "Simple text prompt, response/evaluation is done by same small LLM, not accurate",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "OK",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = PromptRequest::class),
                        examples = [
                            ExampleObject(
                                name = "example-1",
                                description = "With relevancy evaluation",
// @formatter:off
                                value =
                                    """
{
  "prompt": "What is the capital of France?",
  "response": "The capital of France is Paris.",
  "evaluation": { "pass": true, "score": 0.9, "feedback": "correct" }
}
""",
// @formatter:on
                            ),
                            ExampleObject(
                                name = "example-2",
                                description = "Without relevancy evaluation",
// @formatter:off
                                value =
                                    """
{
  "prompt": "What is the capital of France?",
  "response": "The capital of France is Paris.",
  "evaluation": null
}
""",
// @formatter:on
                            ),

                        ],
                    ),
                ],
            ),
        ],
    )
    fun prompt(
        @RequestBody @Valid request: PromptRequest,
        @Parameter(
            description = "Evaluate relevancy of the response",
            example = "false",
        ) @RequestParam(required = false, defaultValue = "false") evaluate: Boolean = false,
    ): PromptResponse

    @Operation(
        summary = "Evaluate prompt/response relevancy using configured LLM backend",
        description = "Simple text prompt/response, evaluation is done by same small LLM, not accurate",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "OK",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = EvaluateRequest::class),
                        examples = [
                            ExampleObject(
                                name = "example-1",
// @formatter:off
                                value =
                                    """
{
  "prompt": "What is the capital of France?",
  "response": "The capital of France is Paris.",
  "evaluation": { "pass": true, "score": 0.9, "feedback": "correct" }
}
""",
// @formatter:on
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/evaluate")
    fun evaluate(
        @RequestBody @Valid request: EvaluateRequest,
    ): PromptResponse
}
