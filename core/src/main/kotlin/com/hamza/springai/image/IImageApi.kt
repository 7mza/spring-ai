package com.hamza.springai.image

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.NotBlank
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.multipart.MultipartFile
import reactor.core.publisher.Flux

@Tag(name = "image", description = "LLM prompting with images")
@RequestMapping(value = ["/api/image"])
interface IImageApi {
    @PostMapping(
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_NDJSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE],
    )
    @Operation(
        summary = "Send a prompt with image to LLM",
        description = "Example image in `./docs/examples/gandalf.jpg`<br /><br />`curl -N` for streaming response",
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
                    Content(mediaType = MediaType.APPLICATION_NDJSON_VALUE, schema = Schema(type = "string")),
                    Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE, schema = Schema(type = "string")),
                ],
            ),
        ],
    )
    fun prompt(
        @NotBlank
        @Schema(description = "prompt to send to LLM", example = "Can you describe this picture in detail?")
        @RequestPart prompt: String,
        @Parameter(description = "Image file format (jpg, png, ...etc)") @RequestPart file: MultipartFile,
    ): Flux<String>
}
