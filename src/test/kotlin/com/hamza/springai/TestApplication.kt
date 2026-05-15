package com.hamza.springai

import org.springframework.boot.fromApplication
import org.springframework.boot.with

fun main(args: Array<String>) {
    fromApplication<Application>().with(OllamaContainer::class, QdrantContainer::class).run(*args)
}
