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
import io.swagger.v3.oas.annotations.parameters.RequestBody as OasRequestBody

@Tag(name = "rag", description = "RAG/Context pull")
@RequestMapping(value = ["/api/rag"], produces = [MediaType.APPLICATION_JSON_VALUE])
interface IRagApi {
    @PostMapping("/manual")
    @Operation(
        summary = "Prompt LLM with manual context pull",
        description = """
Manual context pull (similarity search) from vector store before forwarding request to LLM<br /><br />
To test, upload your documents through `/api/file` (or via MinIO console), and ask a relevant question<br /><br />
(Example files in `./docs/examples/`)
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

    @PostMapping("/advisor/qa")
    @Operation(
        summary = "Prompt LLM with automatic context pull",
        description = """
Automatic context pull from vector store, using `QuestionAnswerAdvisor`, before forwarding request to LLM<br /><br />
To test, upload your documents through `/api/file` (or via MinIO console), and ask a relevant question<br /><br />
(Example files in `./docs/examples/`)
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
{ "prompt": "What is the capital of France?", "response": "The capital of France is Paris." }
""",
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    fun promptWithQAAdvisor(
        @RequestBody @Valid request: RagRequest,
    ): PromptResponse

    @PostMapping("/advisor/modular/enhance")
    @Operation(
        summary = "Prompt LLM with query enhancing",
        description = """
Query enhancements (translation, rewrite, ...etc.) using `RetrievalAugmentationAdvisor` before context pull from vector store, then forwarding request to LLM<br /><br />
To test, upload your documents through `/api/file` (or via MinIO console), and ask a relevant question<br /><br />
(Example files in `./docs/examples/`)
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
{
  "prompt": "Je souhaite obtenir des informations détaillées concernant la capitale officielle de la République française. Pourriez-vous me préciser quelle est la ville qui occupe le rôle de capitale politique, administrative et culturelle de la France ? Je m'intéresse également à son importance historique en tant que siège du gouvernement, ainsi qu'à sa position géographique au sein du territoire français. Quelle est donc cette ville qui abrite les principales institutions de l'État français, telles que le Palais de l'Élysée, l'Assemblée nationale et le Sénat ?",
  "enhancedPrompt": "Capital city of the French Republic and its governmental institutions.",
  "response": "The capital of France is Paris."
}
""",
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    @OasRequestBody(
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = RagRequest::class),
                examples = [
                    ExampleObject(
                        name = "example-0",
                        value = """
{ "prompt": "Je souhaite obtenir des informations détaillées concernant la capitale officielle de la République française. Pourriez-vous me préciser quelle est la ville qui occupe le rôle de capitale politique, administrative et culturelle de la France ? Je m'intéresse également à son importance historique en tant que siège du gouvernement, ainsi qu'à sa position géographique au sein du territoire français. Quelle est donc cette ville qui abrite les principales institutions de l'État français, telles que le Palais de l'Élysée, l'Assemblée nationale et le Sénat ?" }
""",
                    ),
                ],
            ),
        ],
    )
    fun promptWithModularAdvisor(
        @RequestBody @Valid request: RagRequest,
    ): PromptResponse

    @PostMapping("/advisor/modular/expand")
    @Operation(
        summary = "Prompt LLM with query expanding",
        description = """
Query expanding (rewrite N times in different forms for larger vector matching) using `RetrievalAugmentationAdvisor` before context pull from vector store, then forwarding request to LLM<br /><br />
To test, upload your documents through `/api/file` (or via MinIO console), and ask a relevant question<br /><br />
(Example files in `./docs/examples/`)
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
{
  "prompt": "What is the capital of France?",
  "enhancedPrompt": "What is the capital city of France? | France's capital | Official capital of France | Major cities in France and their capitals",
  "response": "The capital of France is Paris."
}
""",
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    fun promptWithExpanding(
        @RequestBody @Valid request: RagRequest,
    ): PromptResponse
}
