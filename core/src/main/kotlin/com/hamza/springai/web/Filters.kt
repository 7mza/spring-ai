package com.hamza.springai.web

import com.hamza.springai.config.NonceFilterProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter
import java.security.SecureRandom
import java.util.Base64

class NonceFilter(
    private val nonceFilterProperties: NonceFilterProperties,
) : OncePerRequestFilter() {
    companion object {
        private val secureRandom = SecureRandom()
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val nonce = generateNonce()
        request.setAttribute("nonce", nonce)
        response.setHeader("X-Nonce", nonce)
        filterChain.doFilter(request, response)
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.servletPath
        return nonceFilterProperties.exclude?.any { path.startsWith(it) } ?: true
    }

    private fun generateNonce(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

internal class Filters
