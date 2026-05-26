package com.hamza.springai.mcp

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
import reactor.core.publisher.Flux
import io.swagger.v3.oas.annotations.parameters.RequestBody as OasRequestBody

@Tag(name = "mcp", description = "MCP usage")
@RequestMapping(value = ["/api/mcp"], produces = [MediaType.APPLICATION_JSON_VALUE])
interface IMcpApi {
    @PostMapping(
        value = ["/file"],
        produces = [MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_NDJSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE],
    )
    @Operation(
        summary = "Ask LLM to do folder/file ops",
        description = """
Anthropic's `MCP Filesystem Server` is running in STDIO mode but wrapped with supergateway for streamableHttp and exposed as a container<br /><br />
Only read only ops on `/projects/**` (which is mapped to the root of this project) are allowed<br /><br />
`curl -N` for streaming response
""",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "OK",
                content = [
                    Content(
                        mediaType = MediaType.TEXT_PLAIN_VALUE,
                        schema = Schema(type = "string"),
                        examples = [
                            ExampleObject(
                                name = "example-0",
                                description = "streamed plain-text output",
                                value = """
Line 1
Line 2
Line 3
""",
                            ),
                        ],
                    ),
                    Content(
                        mediaType = MediaType.APPLICATION_NDJSON_VALUE,
                        schema = Schema(type = "string"),
                    ),
                    Content(
                        mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                        schema = Schema(type = "string"),
                    ),
                ],
            ),
        ],
    )
    fun files(
        @RequestBody @Valid request: McpRequest,
    ): Flux<String>

    @PostMapping(
        value = ["/weather"],
        produces = [MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_NDJSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE],
    )
    @Operation(
        summary = "Ask LLM for the current weather",
        description = """
`./mcp/mcp-weather/` server is running in sync/STDIO modes but wrapped with supergateway for streamableHttp and exposed as a container<br /><br />
`curl -N` for streaming response
""",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "OK",
                content = [
                    Content(
                        mediaType = MediaType.TEXT_PLAIN_VALUE,
                        schema = Schema(type = "string"),
                        examples = [
                            ExampleObject(
                                name = "example-0",
                                description = "streamed plain-text output",
                                value = """
Line 1
Line 2
Line 3
""",
                            ),
                        ],
                    ),
                    Content(
                        mediaType = MediaType.APPLICATION_NDJSON_VALUE,
                        schema = Schema(type = "string"),
                    ),
                    Content(
                        mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                        schema = Schema(type = "string"),
                    ),
                ],
            ),
        ],
    )
    @OasRequestBody(
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = McpRequest::class),
                examples = [
                    ExampleObject(
                        name = "example-0",
                        value = """{ "prompt": "What's the current weather in Salé?" }""",
                    ),
                ],
            ),
        ],
    )
    fun weather(
        @RequestBody @Valid request: McpRequest,
    ): Flux<String>

    @PostMapping(
        value = ["/currency"],
        produces = [MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_NDJSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE],
    )
    @Operation(
        summary = "Ask LLM for the current exchange rate",
        description = """
`./mcp/mcp-currency/` server is running directly in async/streamableHttp modes and exposed as a container<br /><br />
`curl -N` for streaming response
""",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "OK",
                content = [
                    Content(
                        mediaType = MediaType.TEXT_PLAIN_VALUE,
                        schema = Schema(type = "string"),
                        examples = [
                            ExampleObject(
                                name = "example-0",
                                description = "streamed plain-text output",
                                value = """
Line 1
Line 2
Line 3
""",
                            ),
                        ],
                    ),
                    Content(
                        mediaType = MediaType.APPLICATION_NDJSON_VALUE,
                        schema = Schema(type = "string"),
                    ),
                    Content(
                        mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                        schema = Schema(type = "string"),
                    ),
                ],
            ),
        ],
    )
    @OasRequestBody(
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = McpRequest::class),
                examples = [
                    ExampleObject(
                        name = "example-0",
                        value = """
{ "prompt": "What's today's exchange rate between euro and Mozambican/Saudi/Peruvian currencies?" }""",
                    ),
                ],
            ),
        ],
    )
    fun currency(
        @RequestBody @Valid request: McpRequest,
    ): Flux<String>
}
