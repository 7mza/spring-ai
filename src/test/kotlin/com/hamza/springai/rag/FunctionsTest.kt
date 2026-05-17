package com.hamza.springai.rag

import com.hamza.springai.OllamaContainerWithGpu
import com.hamza.springai.QdrantContainer
import com.hamza.springai.rag.file.IFileRepo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.function.context.FunctionCatalog
import org.springframework.context.annotation.Import
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.test.context.ActiveProfiles
import org.springframework.util.DigestUtils
import java.util.concurrent.TimeUnit

/*
 * ingestion pipeline integration tests
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default", "ingestion-test")
@Import(OllamaContainerWithGpu::class, QdrantContainer::class)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FunctionsTest {
    @Autowired
    private lateinit var vectorStore: VectorStore

    @Autowired
    private lateinit var repo: IFileRepo

    @Autowired
    private lateinit var catalog: FunctionCatalog

    @Value($$"${file.supplier.filename-regex}")
    private lateinit var filenameRegex: String

    private fun collectFiles(): Map<String, String> =
        PathMatchingResourcePatternResolver()
            .getResources("classpath:docs/*")
            .filter {
                it.filename?.matches(filenameRegex.toRegex()) == true &&
                    it.contentAsByteArray.isNotEmpty()
            }.associate { r -> r.filename!! to DigestUtils.md5DigestAsHex(r.contentAsByteArray) }

    private fun collectChunks(name: String) =
        vectorStore.similaritySearch(
            SearchRequest
                .builder()
                .query("a")
                .similarityThresholdAll()
                .topK(100)
                .filterExpression("file_name == '$name'")
                .build(),
        )

    @Test
    @Order(1)
    fun `ingestion pipeline should trigger on start and ingest all files from configured path`() {
        // collect files and compute hashes
        val files = collectFiles()
        // wait for pipeline
        await().atMost(1, TimeUnit.MINUTES).until { repo.count() == files.size.toLong() }
        files.forEach { (name, hash) ->
            // check all files were ingested
            assertTrue(repo.existsByHash(hash)) { "file $name was not ingested" }
            // check all files contributed in vector store
            val chunks = collectChunks(name)
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
        val files = collectFiles()
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
        val files = collectFiles()
        await().atMost(1, TimeUnit.MINUTES).until { repo.count() == files.size.toLong() }
        val chunkCountBefore = files.keys.sumOf { collectChunks(it).size }
        // trigger a second pipeline
        catalog.lookup<Runnable>(null).run()
        await()
            .pollDelay(30, TimeUnit.SECONDS)
            .atMost(1, TimeUnit.MINUTES)
            .until {
                // check no new ingestion + no new chuck
                repo.count() == files.size.toLong() &&
                    files.keys.sumOf { collectChunks(it).size } == chunkCountBefore
            }
    }
}
