package com.hamza.springai

import com.hamza.springai.rag.file.File
import com.hamza.springai.rag.file.IFileRepo
import jakarta.persistence.EntityManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.hibernate.stat.Statistics
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.PlatformTransactionManager

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.jpa.properties.hibernate.cache.use_second_level_cache=true"],
)
class JCacheTest {
    @Autowired
    private lateinit var repo: IFileRepo

    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    @Autowired
    private lateinit var txManager: PlatformTransactionManager

    private lateinit var statistics: Statistics

    private val file: File = File(name = "toto.txt", hash = "ebda01362b50602c1e46df160a7b1370")

    @BeforeEach
    fun beforeEach() {
        statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
    }

    @AfterEach
    fun afterEach() {
        repo.deleteAll()
        statistics.clear()
    }

    @Test
    fun `L2 cache should be set immediately on write and don't read from db after`() {
        assertThat(statistics.prepareStatementCount).isZero
        assertThat(statistics.secondLevelCachePutCount).isZero
        assertThat(statistics.secondLevelCacheHitCount).isZero

        // write + read
        val id = repo.saveAndFlush(file).id
        // 2 calls to db
        assertThat(statistics.prepareStatementCount).isEqualTo(2)
        // cache set
        assertThat(statistics.secondLevelCachePutCount).isOne
        // no cache read
        assertThat(statistics.secondLevelCacheHitCount).isZero

        // 1st read
        repo.findById(id)
        // no new call to db (+ 2 previous)
        assertThat(statistics.prepareStatementCount).isEqualTo(2)
        // no new cache set
        assertThat(statistics.secondLevelCachePutCount).isOne
        // cache read
        assertThat(statistics.secondLevelCacheHitCount).isOne
    }
}
