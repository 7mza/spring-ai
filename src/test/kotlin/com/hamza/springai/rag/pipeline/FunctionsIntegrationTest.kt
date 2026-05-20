package com.hamza.springai.rag.pipeline

import com.hamza.springai.IPipelineHelperService
import com.hamza.springai.PipelineHelperService
import com.hamza.springai.TestcontainersConfig
import com.hamza.springai.rag.file.IFileRepo
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
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.util.concurrent.TimeUnit

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["custom.supplier.polling-interval=999999999"],
)
@Import(TestcontainersConfig::class, PipelineHelperService::class)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(Lifecycle.PER_CLASS)
class FunctionsIntegrationTest {
    @Autowired
    private lateinit var helper: IPipelineHelperService

    @Autowired
    private lateinit var vectorStore: VectorStore

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
    @Order(1)
    fun `ingestion pipeline should trigger on start and ingest all files from configured path`() {
        // collect files and compute hashes
        val files = helper.collectFileNameHashPairs()
        // wait for pipeline
        await().atMost(1, TimeUnit.MINUTES).until { repo.count() == files.size.toLong() }
        files.forEach { (name, hash) ->
            // check all files were ingested
            assertTrue(repo.existsByHash(hash)) { "file $name was not ingested" }
            // check all files contributed in vector store
            val chunks = helper.collectDocumentChunks(name)
            assertThat(chunks).isNotEmpty()
            // check pipeline decorated metadata with file_name and file_hash
            chunks.forEach { chunk ->
                assertThat(chunk.metadata["file_name"]).isEqualTo(name)
                assertThat(chunk.metadata["file_hash"]).isEqualTo(hash)
            }
        }
    }

    @Test
    @Order(2)
    fun `after ingestion, similarity search should be done in correct file`() {
        val files = helper.collectFileNameHashPairs()
        await().atMost(1, TimeUnit.MINUTES).until { repo.count() == files.size.toLong() }
        val document = vectorStore.similaritySearch("what is the opposite of hope?").first()
        assertThat(document.metadata["file_name"]).isEqualTo("hope.pdf")
        assertThat(document.text).contains("optimistic", "confidence", "cherish", "anticipation")
        assertThat(document.text)
            .doesNotContainIgnoringCase("Gandalf", "Tolkien", "Hobbit", "Ring", "lion", "Panthera", "chest")
    }

    @Test
    @Order(3)
    fun `pipeline should not re-ingest already ingested files`() {
        val files = helper.collectFileNameHashPairs()
        await().atMost(1, TimeUnit.MINUTES).until { repo.count() == files.size.toLong() }
        val chunkCountBefore = files.keys.sumOf { helper.collectDocumentChunks(it).size }
        // trigger a second pipeline
        functions.pollS3()
        await()
            .atMost(10, TimeUnit.SECONDS)
            .until {
                // check no new ingestion + no new chuck
                repo.count() == files.size.toLong() &&
                    files.keys.sumOf { helper.collectDocumentChunks(it).size } == chunkCountBefore
            }
    }
}
