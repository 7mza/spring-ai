package com.hamza.springai.prompt

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
import org.springframework.web.bind.annotation.RestController

@Tag(name = "prompt", description = "")
@RequestMapping(value = ["/api/prompt"], produces = [MediaType.APPLICATION_JSON_VALUE])
interface IPromptApi {
    @PostMapping("/chat")
    @Operation(
        summary = "send a chat prompt to configured LLM backend",
        description = "TODO",
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
                                name = "example-0",
                                value = """{ "response": "The capital of France is Paris." }""",
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    fun prompt(
        @RequestBody @Valid request: PromptRequest,
    ): PromptResponse

    @Operation(
        summary = "evaluate a prompt/response combo for hallucinations using configured LLM backend",
        description = "TODO",
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
                                name = "example-0",
                                value = """{ "pass": true, "score": 0.9, "feedback": "reasons" }""",
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
    ): EvaluateResponse
}

@RestController
class PromptCtrl(
    private val service: IPromptService,
) : IPromptApi {
    override fun prompt(request: PromptRequest): PromptResponse = service.prompt(request)

    override fun evaluate(request: EvaluateRequest): EvaluateResponse = service.evaluate(request)
}
