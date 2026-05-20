package com.hamza.springai.rag.file

import com.hamza.springai.shared.PageMeta
import com.hamza.springai.shared.SortField
import jakarta.annotation.PreDestroy
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import reactor.core.publisher.Mono
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.services.s3.S3AsyncClient
import java.util.concurrent.Executors

interface IFileService {
    fun existsByHash(hash: String): Boolean

    fun save(file: File): File

    fun findAll(pageable: Pageable): FilesPage

    fun upload(file: MultipartFile): Mono<Void>
}

@Service
class FileService(
    private val repo: IFileRepo,
    private val s3: ObjectProvider<S3AsyncClient>,
    @Value($$"${custom.supplier.remote-dir}") private val bucket: String,
) : IFileService {
    private val vtExecutor = Executors.newVirtualThreadPerTaskExecutor()

    @PreDestroy
    fun destroy() = vtExecutor.shutdown()

    override fun existsByHash(hash: String): Boolean = repo.existsByHash(hash)

    override fun save(file: File): File = repo.save(file)

    override fun upload(file: MultipartFile): Mono<Void> {
        val client = s3.ifAvailable ?: error("S3 autoconf disabled (spring.cloud.aws.s3.enabled=false)")
        val key = file.originalFilename ?: file.name
        return Mono
            .fromFuture {
                client.putObject(
                    {
                        it
                            .bucket(bucket)
                            .key(key)
                            .contentType(file.contentType ?: "application/octet-stream")
                    },
                    AsyncRequestBody.fromInputStream(file.inputStream, file.size, vtExecutor),
                )
            }.then()
    }

    override fun findAll(pageable: Pageable): FilesPage =
        repo
            .findAll(pageable)
            .map { it.toDto() }
            .let {
                FilesPage(
                    content = it.content,
                    page =
                        PageMeta(
                            size = it.size,
                            number = it.number,
                            totalElements = it.totalElements,
                            totalPages = it.totalPages,
                            isFirst = it.isFirst,
                            isLast = it.isLast,
                        ),
                    sort = it.sort.toList().map { field -> SortField(field.property, field.direction) },
                )
            }
}
