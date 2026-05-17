package com.hamza.springai.rag.file

import org.springframework.stereotype.Service

interface IFileService {
    fun existsByHash(hash: String): Boolean

    fun save(file: File): File
}

@Service
class FileService(
    private val repo: IFileRepo,
) : IFileService {
    override fun existsByHash(hash: String): Boolean = repo.existsByHash(hash)

    override fun save(file: File): File = repo.save(file)
}
