package com.hamza.springai.image

import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import reactor.core.publisher.Flux

@RestController
class ImageCtrl(
    private val service: IImageService,
) : IImageApi {
    override fun prompt(
        prompt: String,
        file: MultipartFile,
    ): Flux<String> = service.prompt(prompt, file)
}
