package com.hamza.springai.shared

import org.springframework.core.io.ResourceLoader
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets

class FileReader(
    private val resourceLoader: ResourceLoader,
) {
    fun readFileAsString(path: String): String =
        resourceLoader
            .getResource(path)
            .inputStream
            .use { it.readAllBytes() }
            .let { String(it, StandardCharsets.UTF_8) }
}

class ThrowingMap<K, V>(
    private val delegate: Map<K, V>,
) : Map<K, V> by delegate {
    override fun get(key: K): V = delegate[key] ?: throw AssetNotFoundException(name = key.toString())
}

class AssetManifestReader(
    private val fileReader: FileReader,
    private val objectMapper: ObjectMapper,
) {
    private val assetMap: Map<String, String> by lazy {
        ThrowingMap(
            delegate =
                fileReader
                    .readFileAsString("classpath:/static/dist/asset-manifest.json")
                    .let {
                        objectMapper
                            .readValue(it, object : TypeReference<Map<String, String>>() {})
                    },
        )
    }

    fun getAll(): Map<String, String> = assetMap
}
