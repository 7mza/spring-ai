package com.hamza.springai.web

import com.hamza.springai.shared.AssetManifestReader
import org.springframework.beans.propertyeditors.StringTrimmerEditor
import org.springframework.web.bind.WebDataBinder
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.InitBinder
import org.springframework.web.bind.annotation.ModelAttribute

@ControllerAdvice
class GlobalModelAttributes(
    private val assetManifestReader: AssetManifestReader,
) {
    @ModelAttribute("assetManifest")
    fun assetManifest(): Map<String, String> = assetManifestReader.getAll()

    @ModelAttribute("theme")
    fun theme(
        @CookieValue(value = "theme", required = false) theme: String?,
    ): String = if (theme.equals("dark", true)) "dark" else "light"

    @InitBinder
    fun initBinder(binder: WebDataBinder) {
        binder.registerCustomEditor(String::class.java, StringTrimmerEditor(false))
    }
}
