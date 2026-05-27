package com.hamza.springai.image

import com.hamza.springai.OllamaContainerConfig
import org.assertj.core.api.Assertions.assertThat
import org.junitpioneer.jupiter.RetryingTest
import org.slf4j.LoggerFactory
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.core.io.ClassPathResource
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.multipart.MultipartFile
import reactor.test.StepVerifier

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.ai.ollama.chat.model=moondream:1.8b"], // vision enabled model
)
@Import(OllamaContainerConfig::class)
class ImageServiceTest {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Autowired
    private lateinit var service: IImageService

    @MockitoBean // prevent autoconf of embedding vector store, not needed in this test
    private lateinit var vectorStore: VectorStore

    @MockitoBean("chatMemoryVectorStore") // prevent autoconf of memory vector store, not needed in this test
    private lateinit var chatMemoryVectorStore: VectorStore

    private val prompt = "Can you describe this picture in detail?"

    private val file: MultipartFile =
        MockMultipartFile(
            "file",
            "image.jpg",
            "image/jpeg",
            ClassPathResource("docs/image.jpg").inputStream,
        )

    // retry N times because small models are unreliable
    @RetryingTest(maxAttempts = 3, suspendForMs = 1000)
    fun prompt() {
        val keywords = listOf("man", "old", "grey", "Gandalf", "beard", "hat", "staff", "nature")
        val threshold = keywords.size / 2 // 50%
        StepVerifier
            .create(service.prompt(prompt, file).collectList())
            .assertNext {
                val response = it.joinToString("")
                val matches = keywords.count { keyword -> response.contains(keyword, ignoreCase = true) }
                assertThat(matches)
                    .withFailMessage("expected $threshold keyword matches but got only $matches")
                    .isGreaterThanOrEqualTo(threshold)
                logger.debug("response: {}", response)
            }.verifyComplete()
    }
}
