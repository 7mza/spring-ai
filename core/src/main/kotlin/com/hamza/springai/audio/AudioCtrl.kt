package com.hamza.springai.audio

import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
class AudioCtrl(
    private val service: IAudioService,
) : IAudioApi {
    override fun stt(file: MultipartFile): String = service.stt(file)

    override fun tts(
        text: String,
        voice: Voice,
    ): ByteArray = service.tts(text, voice)
}
