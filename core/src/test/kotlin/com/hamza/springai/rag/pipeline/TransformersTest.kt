package com.hamza.springai.rag.pipeline

import com.hamza.springai.IPipelineHelperService
import com.hamza.springai.MinioTestContainerConfig
import com.hamza.springai.OllamaContainerConfig
import com.hamza.springai.PipelineHelperService
import com.hamza.springai.QdrantContainerConfig
import com.hamza.springai.rag.file.IFileRepo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junitpioneer.jupiter.RetryingTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.util.concurrent.TimeUnit

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.cloud.aws.s3.enabled=true",
        "spring.cloud.function.definition=customS3Supplier|duplicationFilter|documentReader|documentSplitter|languageEnricher|vectorStoreWriter|s3Archiver",
    ],
)
@Import(
    MinioTestContainerConfig::class,
    OllamaContainerConfig::class,
    QdrantContainerConfig::class,
    PipelineHelperService::class,
)
@TestInstance(Lifecycle.PER_CLASS)
class TransformersTest {
    @Autowired
    private lateinit var helper: IPipelineHelperService

    @Autowired
    private lateinit var repo: IFileRepo

    private lateinit var files: Map<String, String>

    @BeforeAll
    fun beforeAll() {
        // collect files and compute hashes once
        files = helper.collectFileNameHashPairs()
        // upload test files to bucket
        helper.initBucket("default")
        // wait for ingestion
        await().atMost(2, TimeUnit.MINUTES).until { repo.count() == files.size.toLong() }
    }

    // retry N times because small models are unreliable
    @RetryingTest(maxAttempts = 3, suspendForMs = 1000)
    fun `languageEnricher is correctly adding language metadata`() {
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
