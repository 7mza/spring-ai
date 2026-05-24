package com.hamza.springai.rag.file

import com.hamza.springai.shared.PageMeta
import com.hamza.springai.shared.SortField

data class FileDto(
    val id: String,
    val name: String,
    val hash: String,
    val createdAt: String,
)

data class FilesPage(
    val content: List<FileDto>,
    val page: PageMeta,
    val sort: List<SortField>,
)
