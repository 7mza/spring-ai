package com.hamza.springai.rag.file

import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import reactor.core.publisher.Mono

@RestController
class FileCtrl(
    private val service: IFileService,
) : IFileApi {
    override fun findAll(pageable: Pageable): FilesPage = service.findAll(pageable)

    override fun upload(file: MultipartFile): Mono<Void> = service.upload(file)
}
