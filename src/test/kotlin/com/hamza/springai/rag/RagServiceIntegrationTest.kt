package com.hamza.springai.rag

import com.hamza.springai.OllamaContainerWithGpu
import com.hamza.springai.QdrantContainer
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@Disabled
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default", "ingestion-test")
@Import(OllamaContainerWithGpu::class, QdrantContainer::class)
class RagServiceIntegrationTest {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Autowired
    private lateinit var service: IRagService

    private val prompt = ""

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
