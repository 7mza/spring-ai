package com.hamza.springai.prompt

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "prompt", description = "")
@RequestMapping(value = ["/api/prompt"], produces = [MediaType.APPLICATION_JSON_VALUE])
interface IPromptApi {
    @PostMapping
    @Operation(
        summary = "send a prompt to configured LLM backend",
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
// @formatter:off
                                value =
"""
{
  "response": "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."
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
    fun prompt(request: PromptRequest): PromptResponse
}

@RestController
class PromptCtrl(
    private val service: IPromptService,
) : IPromptApi {
    override fun prompt(request: PromptRequest): PromptResponse = service.prompt(request)
}
