package com.hamza.springai.memory

import com.hamza.springai.prompt.PromptResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
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
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping

@Tag(name = "memory", description = "LLM prompting with chat memory")
@RequestMapping(value = ["/api/memory"], produces = [MediaType.APPLICATION_JSON_VALUE])
interface IMemoryApi {
    @PostMapping("/jdbc")
    @Operation(
        summary = "Prompt LLM with memory enabled",
        description = """
Conversation memory is enabled through `JDBC` backend<br /><br />
To test, keep prompting about the same topic or changing conversationId
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
                                value = """
{ "prompt": "Who is Gandalf?", "response": "Gandalf is a primary protagonist in the lord of the rings universe." }
""",
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    fun promptWithJdbcMemory(
        @Parameter(
            `in` = ParameterIn.HEADER,
            description = "Conversation ID, to track current chat memory session",
        )
        @RequestHeader(value = "X_CONVERSATION_ID", defaultValue = "default") conversationId: String,
        @RequestBody @Valid request: MemoryRequest,
    ): PromptResponse

    @PostMapping("/vector")
    @Operation(
        summary = "Prompt LLM with memory enabled",
        description = """
Conversation memory is enabled through `VectorStore` backend<br /><br />
To test, keep prompting about the same topic or changing conversationId
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
                                value = """
{ "prompt": "Who is Gandalf?", "response": "Gandalf is a primary protagonist in the lord of the rings universe." }
""",
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    fun promptWithVectorStoreMemory(
        @Parameter(
            `in` = ParameterIn.HEADER,
            description = "Conversation ID, to track current chat memory session",
        )
        @RequestHeader(value = "X_CONVERSATION_ID", defaultValue = "default") conversationId: String,
        @RequestBody @Valid request: MemoryRequest,
    ): PromptResponse
}
