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
    @PostMapping
    @Operation(
        summary = "Send a chat prompt to configured LLM backend",
        description = """
Will pull context (similarity search) from vector store before forwarding request to LLM.<br /><br />
`*.dummy.md` in `INGEST_DIR` (./docs/) contains some hallucinated facts for testing.<br /><br />
Add your documents in `INGEST_DIR` for ingestion pipeline to trigger.
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
{ "prompt": "Who is Gretchen Faulhauser?", "response": "RAG augmented response..." }
""",
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    fun prompt(
        @RequestBody @Valid request: RagRequest,
    ): PromptResponse
}
