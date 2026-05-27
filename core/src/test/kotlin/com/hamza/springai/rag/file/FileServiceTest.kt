package com.hamza.springai.rag.file

import com.hamza.springai.IPipelineHelperService
import com.hamza.springai.MinioTestContainerConfig
import com.hamza.springai.OllamaContainerConfig
import com.hamza.springai.PipelineHelperService
import com.hamza.springai.QdrantContainerConfig
import com.hamza.springai.data.encodeToString
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockMultipartFile
import org.springframework.util.DigestUtils.md5DigestAsHex
import java.util.concurrent.TimeUnit

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.cloud.aws.s3.enabled=true"],
)
@Import(
    MinioTestContainerConfig::class,
    OllamaContainerConfig::class,
    QdrantContainerConfig::class,
    PipelineHelperService::class,
)
@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FileServiceTest {
    @Autowired
    private lateinit var service: IFileService

    @Autowired
    private lateinit var helper: IPipelineHelperService

    @Autowired
    private lateinit var repo: IFileRepo

    private lateinit var files: Map<String, String>

    private lateinit var testFile: File

    @BeforeAll
    fun beforeAll() {
        // collect files and compute hashes once
        files = helper.collectFileNameHashPairs()
        // upload test files to bucket
        helper.initBucket("default")
        // wait for ingestion
        await().atMost(1, TimeUnit.MINUTES).until { repo.count() == files.size.toLong() }
        // get a random test file
        testFile = repo.findAll().random()
    }

    @Test
    @Order(1)
    fun deleteById() {
        // delete
        service.deleteById(testFile.id.id.encodeToString())
        // check related vectors removed
        assertThat(helper.collectDocumentChunks(testFile.name)).isEmpty()
        // check related S3 object removed
        assertThat(helper.existsInS3("default", "processed/${testFile.name}")).isFalse
        // check related DB entry removed
        assertThat(repo.existsById(testFile.id)).isFalse
    }

    @Test
    @Order(2)
    fun deleteAll() {
        // delete
        service.deleteAll()
        files.keys.forEach {
            // check all vectors removed
            assertThat(helper.collectDocumentChunks(it)).isEmpty()
            // check all S3 objects removed
            assertThat(helper.existsInS3("default", "processed/$it")).isFalse
        }
        // check all DB entries removed
        assertThat(repo.count()).isZero
    }

    @Test
    @Order(3)
    fun upload() {
        // create test multipart file
        val content = "hello world".toByteArray()
        val hash = md5DigestAsHex(content)
        val name = "test.txt"
        val file = MockMultipartFile("file", name, "text/plain", content)
        // upload it using service
        service.upload(file).block()
        // wait for next ingestion poll
        await().atMost(1, TimeUnit.MINUTES).until { repo.count() == 1L }
        // check new file was ingested
        assertTrue(repo.existsByHash(hash)) { "file $name was not ingested" }
        // check new file contributed in vector store
        val chunks = helper.collectDocumentChunks(name)
        assertThat(chunks).isNotEmpty()
        // check pipeline decorated metadata with file_name and file_hash
        chunks.forEach { chunk ->
            assertThat(chunk.metadata["file_name"]).isEqualTo(name)
            assertThat(chunk.metadata["file_hash"]).isEqualTo(hash)
        }
        // check new file exists in S3
        assertThat(helper.existsInS3("default", "processed/$name")).isTrue
    }
}
