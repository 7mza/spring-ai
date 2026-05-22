package com.hamza.springai.tool

import com.hamza.springai.rag.file.FilesPage
import org.springframework.web.bind.annotation.RestController

@RestController
class ToolCtrl(
    private val service: IToolService,
) : IToolApi {
    override fun getCurrentTimeAt(location: String): String = service.getCurrentTimeAt(location)

    override fun listIngestedFiles(
        size: Int,
        page: Int,
    ): FilesPage = service.listIngestedFiles(size, page)
}
