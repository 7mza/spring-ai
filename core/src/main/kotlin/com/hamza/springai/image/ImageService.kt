package com.hamza.springai.image

import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Service
import org.springframework.util.MimeTypeUtils
import org.springframework.web.multipart.MultipartFile
import reactor.core.publisher.Flux

interface IImageService {
    fun prompt(
        prompt: String,
        file: MultipartFile,
    ): Flux<String>
}

@Service
class ImageService(
    chatClientBuilder: ChatClient.Builder,
) : IImageService {
    private val chatClient = chatClientBuilder.build()

    override fun prompt(
        prompt: String,
        file: MultipartFile,
    ): Flux<String> =
        chatClient
            .prompt()
            .user {
                it
                    .text(prompt)
                    .media(
                        MimeTypeUtils.parseMimeType(file.contentType ?: "application/octet-stream"),
                        file.resource,
                    )
            }.stream()
            .content()
}
