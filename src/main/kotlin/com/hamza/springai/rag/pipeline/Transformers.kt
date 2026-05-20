package com.hamza.springai.rag.pipeline

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.document.DocumentTransformer
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service

interface ITransformers {
    fun languageEnricher(): DocumentTransformer

    fun qualityEvaluator(): DocumentTransformer
}

@Service
class Transformers(
    chatClientBuilder: ChatClient.Builder,
    @Value("classpath:/prompt_templates/rag/language.st") private val language: Resource,
    @Value("classpath:/prompt_templates/rag/quality.st") private val quality: Resource,
) : ITransformers {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val chatClient = chatClientBuilder.build()

    override fun languageEnricher(): DocumentTransformer =
        DocumentTransformer { docs ->
            docs.map { doc ->
                val language =
                    chatClient
                        .prompt()
                        .user { it.text(language).param("text", doc.text!!) }
                        .call()
                        .content()
                        ?.trim() ?: "unknown"
                doc.mutate().metadata("language", language).build()
            }
        }

    override fun qualityEvaluator(): DocumentTransformer =
        DocumentTransformer { docs ->
            docs.filter { doc ->
                val response =
                    chatClient
                        .prompt()
                        .user { it.text(quality).param("text", doc.text!!) }
                        .call()
                        .content() ?: "false"
                response
                    .trim()
                    .lowercase()
                    .startsWith("true")
                    .also {
                        if (!it) {
                            logger.warn(
                                "file: {}, dropping low quality chunk: {}",
                                doc.metadata["file_name"],
                                doc.text?.take(60),
                            )
                        }
                    }
            }
        }
}
