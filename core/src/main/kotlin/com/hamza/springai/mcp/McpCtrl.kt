package com.hamza.springai.mcp

import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux

@RestController
class McpCtrl(
    private val service: IMcpService,
) : IMcpApi {
    override fun files(request: McpRequest): Flux<String> = service.prompt(request)

    override fun weather(request: McpRequest): Flux<String> = service.prompt(request)
}
