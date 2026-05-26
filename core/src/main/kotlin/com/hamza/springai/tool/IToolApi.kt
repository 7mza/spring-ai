package com.hamza.springai.tool

import com.hamza.springai.rag.file.FilesPage
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Tag(name = "tool", description = "Tool usage")
@RequestMapping(value = ["/api/tool"], produces = [MediaType.APPLICATION_JSON_VALUE])
interface IToolApi {
    @GetMapping("/time", produces = [MediaType.TEXT_PLAIN_VALUE])
    @Operation(
        summary = "Ask LLM for the current time at a time zone",
        description = "Simple tool with string input and string output",
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
                                description = "",
                                value = "The current time in Riyadh is 0:00 PM.",
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    fun getCurrentTimeAt(
        @Parameter(description = "Which location", example = "Riyadh") @RequestParam location: String,
    ): String

    @GetMapping("/file")
    @Operation(
        summary = "Ask LLM for the list of ingested files",
        description = """
Complex tool with multi input, JSON output, DB call and retry/recover<br /><br />
Might give Jackson errors if LLM doesn't respect JSON format
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
                        schema = Schema(implementation = FilesPage::class),
                        examples = [
                            ExampleObject(
                                name = "example-0",
                                description = "",
                                value = """
{
  "content": [
    {
      "id": "10QHtVYe0ul",
      "name": "file1.pdf",
      "hash": "d6756726683d9aacb09b8b71a2e76319",
      "createdAt": "2026-05-20T20:44:12.262216Z"
    },
    {
      "id": "10Q6HwWxRRV",
      "name": "file2.txt",
      "hash": "68d72ba93e5b308c5e32d293fa65b13d",
      "createdAt": "2026-05-20T18:01:47.790848Z"
    }
  ],
  "page": {
    "isFirst": true,
    "isLast": false,
    "number": 0,
    "size": 2,
    "totalPages": 3,
    "totalElements": 5
  },
  "sort": []
}
""",
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    fun listIngestedFiles(
        @Parameter(description = "Size of files", example = "10") @RequestParam size: Int,
        @Parameter(description = "Page number", example = "0") @RequestParam page: Int,
    ): FilesPage
}
