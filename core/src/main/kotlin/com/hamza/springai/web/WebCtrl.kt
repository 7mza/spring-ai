package com.hamza.springai.web

import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller

@Controller
class WebCtrl : IWebApi {
    override fun noFavicon(): ResponseEntity<Void> = ResponseEntity.noContent().build()

    override fun index(): String = "index"
}
