package com.hamza.springai.audio

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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.multipart.MultipartFile

@Tag(name = "audio", description = "STT/TTS")
@RequestMapping(value = ["/api/audio"])
interface IAudioApi {
    @PostMapping(
        value = ["/stt"],
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.TEXT_PLAIN_VALUE],
    )
    @Operation(
        summary = "speech to text",
        description = "Example audio in `./docs/examples/gandalf.wav`",
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
                                description = "audio file transcription",
                                value = "bla bla bla",
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    fun stt(
        @Parameter(description = "Audio file format (mp3, wav, ...etc)") @RequestPart file: MultipartFile,
    ): String

    @GetMapping(
        value = ["/tts"],
        produces = ["audio/mpeg"],
    )
    @Operation(
        summary = "text to speech",
        description = """
mp3 response<br /><br />
You can play audio directly in swagger-ui or copy generate curl and append ` > audio.mp3` to it for download<br /><br />
(Should be a POST but using GET for html audio player to work)
""",
    )
    @ApiResponses(
        value = [ApiResponse(responseCode = "200", description = "OK", content = [Content(mediaType = "audio/mpeg")])],
    )
    fun tts(
        @NotBlank
        @Parameter(
            description = "text to synthesize",
            example = """
Hope is an optimistic state of mind that is based on an expectation of desirable outcomes. Among its opposites are hopelessness, and despair.
""",
        )
        @RequestParam
        text: String,
        @Parameter(description = "model voice")
        @RequestParam(defaultValue = "AF_SKY") voice: Voice,
    ): ByteArray
}
