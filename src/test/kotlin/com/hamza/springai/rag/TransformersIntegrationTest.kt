package com.hamza.springai.rag

import com.hamza.springai.IPipelineHelperService
import com.hamza.springai.OllamaContainerWithGpu
import com.hamza.springai.PipelineHelperService
import com.hamza.springai.QdrantContainer
import com.hamza.springai.rag.file.IFileRepo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.TimeUnit

/*
 * transformers integration tests
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        """spring.cloud.function.definition=\
fileSupplier|duplicationFilter|documentReader|documentSplitter|languageEnricher|vectorStoreWriter""",
    ],
)
@ActiveProfiles("default", "ingestion-test")
@Import(OllamaContainerWithGpu::class, QdrantContainer::class, PipelineHelperService::class)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class TransformersIntegrationTest {
    @Autowired
    private lateinit var helper: IPipelineHelperService

    @Autowired
    private lateinit var repo: IFileRepo

    @Test
    fun `languageEnricher is correctly adding language metadata`() {
        val files = helper.collectFiles()
        await().atMost(1, TimeUnit.MINUTES).until { repo.count() == files.size.toLong() }
        assertThat(
            helper
                .collectChunks("amal.txt")
                .first()
                .metadata["language"]
                .toString(),
        ).isEqualToIgnoringCase("ar")
        assertThat(
            helper
                .collectChunks("espoir.txt")
                .first()
                .metadata["language"]
                .toString(),
        ).isEqualToIgnoringCase("fr")
        assertThat(
            helper
                .collectChunks("gandalf.txt")
                .first()
                .metadata["language"]
                .toString(),
        ).isEqualToIgnoringCase("en")
        assertThat(
            helper
                .collectChunks("hope.pdf")
                .first()
                .metadata["language"]
                .toString(),
        ).isEqualToIgnoringCase("en")
        assertThat(
            helper
                .collectChunks("lion.md")
                .first()
                .metadata["language"]
                .toString(),
        ).isEqualToIgnoringCase("en")
    }
}
