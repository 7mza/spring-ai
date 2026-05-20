package com.hamza.springai.rag.pipeline

import com.hamza.springai.IPipelineHelperService
import com.hamza.springai.PipelineHelperService
import com.hamza.springai.TestcontainersConfig
import com.hamza.springai.rag.file.IFileRepo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.util.concurrent.TimeUnit

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        """spring.cloud.function.definition=\
customS3Supplier|duplicationFilter|documentReader|documentSplitter|languageEnricher|vectorStoreWriter|s3Archiver""",
        "custom.supplier.polling-interval=999999999",
    ],
)
@Import(TestcontainersConfig::class, PipelineHelperService::class)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(Lifecycle.PER_CLASS)
class TransformersIntegrationTest {
    @Autowired
    private lateinit var helper: IPipelineHelperService

    @Autowired
    private lateinit var repo: IFileRepo

    @Autowired
    private lateinit var functions: Functions

    @BeforeAll
    fun beforeAll() {
        helper.initBucket("default")
        functions.pollS3()
    }

    @Test
    fun `languageEnricher is correctly adding language metadata`() {
        val files = helper.collectFileNameHashPairs()
        await().atMost(2, TimeUnit.MINUTES).until { repo.count() == files.size.toLong() }
        assertThat(
            helper
                .collectDocumentChunks("amal.txt")
                .first()
                .metadata["language"]
                .toString(),
        ).isEqualToIgnoringCase("ar")
        assertThat(
            helper
                .collectDocumentChunks("espoir.txt")
                .first()
                .metadata["language"]
                .toString(),
        ).isEqualToIgnoringCase("fr")
        assertThat(
            helper
                .collectDocumentChunks("gandalf.txt")
                .first()
                .metadata["language"]
                .toString(),
        ).isEqualToIgnoringCase("en")
        assertThat(
            helper
                .collectDocumentChunks("hope.pdf")
                .first()
                .metadata["language"]
                .toString(),
        ).isEqualToIgnoringCase("en")
        assertThat(
            helper
                .collectDocumentChunks("lion.md")
                .first()
                .metadata["language"]
                .toString(),
        ).isEqualToIgnoringCase("en")
    }
}
