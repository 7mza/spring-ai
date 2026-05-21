package com.hamza.springai.rag.file

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.multipart.MultipartFile
import reactor.core.publisher.Mono

@Tag(name = "file", description = "Files/Documents ingestion")
@RequestMapping(value = ["/api/file"], produces = [MediaType.APPLICATION_JSON_VALUE])
interface IFileApi {
    @Operation(
        summary = "List ingested files",
        description = "",
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
  "sort": [
    {
      "property": "name",
      "direction": "ASC"
    }
  ]
}
""",
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    @GetMapping
    fun findAll(
        @ParameterObject pageable: Pageable,
    ): FilesPage

    @Operation(
        summary = "Upload a file for ingestion",
        description = """
Puts the file in the MinIO bucket root. Ingestion pipeline picks it up on next poll.<br /><br />
Max size is configured via YAML: `spring.servlet.multipart.max-file-size=100MB`""",
    )
    @ApiResponse(responseCode = "202", description = "Accepted")
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun upload(
        @RequestPart("file") file: MultipartFile,
    ): Mono<Void>

    @Operation(
        summary = "Delete an ingested file by id",
        description = "Removes from DB, vector store, and S3 `processed/`.",
    )
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "Not found")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteById(
        @PathVariable id: String,
    )

    @Operation(summary = "Delete all ingested files", description = "Wipes DB, vector store, and S3 `processed/`.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAll()
}
