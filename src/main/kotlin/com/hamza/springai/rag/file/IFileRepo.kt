package com.hamza.springai.rag.file

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
interface IFileRepo : JpaRepository<File, FileId> {
    fun existsByHash(hash: String): Boolean
}
