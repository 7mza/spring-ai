package com.hamza.springai.rag

import com.hamza.springai.IPipelineHelperService
import com.hamza.springai.MinioTestContainerConfig
import com.hamza.springai.OllamaContainerConfig
import com.hamza.springai.PipelineHelperService
import com.hamza.springai.QdrantContainerConfig
import com.hamza.springai.rag.file.IFileRepo
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.util.concurrent.TimeUnit

@Disabled
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "custom.supplier.polling-interval=1000", // ingest as fast as possible
        "spring.cloud.aws.s3.enabled=true",
    ],
)
@Import(
    MinioTestContainerConfig::class,
    OllamaContainerConfig::class,
    QdrantContainerConfig::class,
    PipelineHelperService::class,
)
@TestInstance(Lifecycle.PER_CLASS)
class RagServiceIntegrationTest {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Autowired
    private lateinit var service: IRagService

    @Autowired
    private lateinit var repo: IFileRepo

    @Autowired
    private lateinit var helper: IPipelineHelperService

    private lateinit var files: Map<String, String>

    private val prompt = ""

    @BeforeAll
    fun beforeAll() {
        // collect files and compute hashes once
        files = helper.collectFileNameHashPairs()
        // upload test files to bucket
        helper.initBucket("default")
        // wait for ingestion
        await().atMost(1, TimeUnit.MINUTES).until { repo.count() == files.size.toLong() }
    }

    @Test
    fun pullContext() {
        // FIXME
    }

    @Test
    fun promptWithManualRag() {
        // FIXME
    }

    @Test
    fun promptWithAdvisor() {
        // FIXME
    }
}
