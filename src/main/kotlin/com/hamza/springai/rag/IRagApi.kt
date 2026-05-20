package com.hamza.springai.rag

import com.hamza.springai.prompt.PromptResponse
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

@Tag(name = "rag", description = "")
@RequestMapping(value = ["/api/rag"], produces = [MediaType.APPLICATION_JSON_VALUE])
interface IRagApi {
    @PostMapping("/manual")
    @Operation(
        summary = "Send a prompt to LLM",
        description = """
Manual context pull (similarity search) from vector store before forwarding request to LLM.<br /><br />
To test, upload your documents through `/api/file` or via MinIO console. Ingestion pipeline picks it up on next poll.
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
{ "prompt": "What is the capital of France?", "response": "The capital of France is Paris." }
""",
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    fun promptWithManualRag(
        @RequestBody @Valid request: RagRequest,
    ): PromptResponse

    @PostMapping("/advisor")
    @Operation(
        summary = "Send a prompt to LLM",
        description = """
Automatic context pull (using advisor) from vector store before forwarding request to LLM.<br /><br />
To test, upload your documents through `/api/file` or via MinIO console. Ingestion pipeline picks it up on next poll.
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
{ "prompt": "What is the capital of France?", "response": "The capital of France is Paris." }
""",
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    fun promptWithAdvisor(
        @RequestBody @Valid request: RagRequest,
    ): PromptResponse
}
