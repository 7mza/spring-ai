package com.hamza.springai.audio

import org.springframework.ai.audio.tts.TextToSpeechPrompt
import org.springframework.ai.openai.OpenAiAudioSpeechModel
import org.springframework.ai.openai.OpenAiAudioSpeechOptions
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

interface IAudioService {
    fun stt(file: MultipartFile): String

    fun tts(
        text: String,
        voice: Voice,
    ): ByteArray
}

@Service
class AudioService(
    private val transcriptionModel: OpenAiAudioTranscriptionModel,
    private val speechModel: OpenAiAudioSpeechModel,
) : IAudioService {
    override fun stt(file: MultipartFile): String = transcriptionModel.transcribe(file.resource)

    override fun tts(
        text: String,
        voice: Voice,
    ): ByteArray =
        speechModel
            .call(
                TextToSpeechPrompt(
                    text,
                    OpenAiAudioSpeechOptions
                        .builder()
                        .from(speechModel.options)
                        .voice(voice.id)
                        .build(),
                ),
            ).result
            .output
}
