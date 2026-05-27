package com.hamza.springai.tool

import com.hamza.springai.rag.file.FileDto
import com.hamza.springai.rag.file.FilesPage
import com.hamza.springai.rag.file.IFileService
import com.hamza.springai.shared.PageMeta
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.responseEntity
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.data.domain.Pageable
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.retry.support.RetrySynchronizationManager
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import tools.jackson.core.JacksonException
import java.time.LocalDateTime
import java.time.ZoneId

interface IToolService {
    fun getCurrentTimeAt(location: String): String

    fun listIngestedFiles(
        size: Int,
        page: Int,
    ): FilesPage
}

@Service
class ToolService(
    chatClientBuilder: ChatClient.Builder,
    tools: ITools,
) : IToolService {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val chatClient =
        chatClientBuilder
            .defaultTools(tools)
            // OR: .defaultToolNames("getTimeAt", "listFiles")
            .build()

    private val timeTemplate = "What is the current time in {location}?"
    private val fileTemplate =
        """
        You MUST call listFiles tool with page={page} and size={size}.
        Return the tool result as-is in raw JSON. Do not summarize, narrate, or add any explanation.
        Do not generate or invent any data. Only return what the tool gives you.
        """.trimIndent()

    override fun getCurrentTimeAt(location: String): String =
        chatClient
            .prompt()
            .user { it.text(timeTemplate).param("location", location) }
            // OR: .tools(tools)
            // OR: .toolNames("getTimeAt", "listFiles")
            .call()
            .content()
            ?: error("LLM response was null")

    @Retryable(
        retryFor = [JacksonException::class, NumberFormatException::class],
        maxAttempts = 3,
        backoff = Backoff(1000),
    )
    override fun listIngestedFiles(
        size: Int,
        page: Int,
    ): FilesPage {
        val attempt = RetrySynchronizationManager.getContext()?.retryCount ?: 0
        if (attempt > 0) logger.warn("LLM response parsing failed, retry attempt {}", attempt)
        return chatClient
            .prompt()
            .user {
                it
                    .text(fileTemplate)
                    .param("size", size)
                    .param("page", page)
            }.call()
            .responseEntity<FilesPage>()
            .also { logger.info("listIngestedFiles: {}", it) }
            .entity()!!
    }

    @Recover
    fun listIngestedFiles(
        ex: Exception,
        size: Int,
        page: Int,
    ): FilesPage {
        logger.warn("all LLM response parsing failed, applying recovery")
        return FilesPage(
            content =
                listOf(
                    FileDto(
                        id = "LLM response parsing failed",
                        name = "",
                        hash = "",
                        createdAt = "",
                    ),
                ),
            page =
                PageMeta(
                    isFirst = false,
                    isLast = false,
                    number = 0,
                    totalElements = 0,
                    size = 0,
                    totalPages = 0,
                ),
            sort = listOf(),
        )
    }
}

interface ITools {
    fun getTimeAt(timeZone: String): String

    fun listFiles(
        size: Int,
        page: Int,
    ): FilesPage
}

@Component
class Tools(
    private val service: IFileService,
) : ITools {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Tool(
        name = "getTimeAt",
        description = """
Get the current date and time for a given location.
Use when the user asks what time or date it is in a specific city, country, or region.
Do not use for time arithmetic or timezone conversions, only for the current wall-clock time.
Read-only, no side effects.
Returns an ISO-8601 local datetime string, e.g. '2026-05-22T21:00:00', with no timezone offset included.
""",
    )
    override fun getTimeAt(
        @ToolParam(
            description = """
IANA timezone ID for the target location, e.g. 'Asia/Tokyo', 'America/New_York', 'Europe/Paris'.
"Must be a valid IANA ID — do not pass city names, abbreviations like 'JST', or UTC offsets like 'UTC+9'.,
""",
        )
        timeZone: String,
    ): String {
        logger.info("getting time at $timeZone")
        return LocalDateTime.now(ZoneId.of(timeZone)).toString()
    }

    @Tool(
        name = "listFiles",
        description = """
List files that have been ingested into the knowledge base.
Use when the user asks what documents, files, or content is available.
Read-only, no side effects.
Returns a paginated list of file names and their ingestion date.
""",
    )
    override fun listFiles(
        @ToolParam(description = "Number of files to return per page. Default to 10 if not specified by the user.")
        size: Int,
        @ToolParam(description = "Page number, zero-based. Use 0 for the first page.")
        page: Int,
    ): FilesPage = service.findAll(Pageable.ofSize(size).withPage(page))
}
