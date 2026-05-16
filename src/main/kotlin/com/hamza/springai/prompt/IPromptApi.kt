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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Tag(name = "prompt", description = "")
@RequestMapping(value = ["/api/prompt"], produces = [MediaType.APPLICATION_JSON_VALUE])
interface IPromptApi {
    @PostMapping
    @Operation(
        summary = "Send a chat prompt to configured LLM backend",
        description = """
Return a **simple text response** with no parsing.<br />
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
                        schema = Schema(implementation = PromptResponse::class),
                        examples = [
                            ExampleObject(
                                name = "example-0",
                                description = "",
                                value = """
{ "prompt": "What is the capital of France?", "response": "The capital of France is Paris." }""",
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

    @GetMapping
    @Operation(
        summary = "Ask LLM to generate a list of the top songs",
        description = """
Return a **collection of Objects response** from LLM with automatic parsing.<br />
Retry mechanism for parsing errors.<br />
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
                        schema = Schema(implementation = SongResponse::class),
                        examples = [
                            ExampleObject(
                                name = "example-0",
                                description = "",
                                value = """{ "response": [ { "title": "title1" }, { "title": "title2" }] }""",
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    fun songs(
        @Parameter(
            description = "Which year",
            example = "2006",
        ) @RequestParam(required = true) year: Int = 2006,
    ): SongResponse
}
