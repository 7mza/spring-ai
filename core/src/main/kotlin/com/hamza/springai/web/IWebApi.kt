package com.hamza.springai.web

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@RequestMapping(produces = [MediaType.TEXT_HTML_VALUE])
interface IWebApi {
    @GetMapping("/favicon.ico")
    fun noFavicon(): ResponseEntity<Void>

    @GetMapping(path = ["/"])
    fun index(): String
}
