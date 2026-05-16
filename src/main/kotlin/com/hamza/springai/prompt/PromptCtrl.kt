package com.hamza.springai.prompt

import org.springframework.web.bind.annotation.RestController

@RestController
class PromptCtrl(
    private val service: IPromptService,
) : IPromptApi {
    override fun prompt(request: PromptRequest): PromptResponse = service.prompt(request)

    override fun songs(year: Int): SongResponse = service.songs(year)
}
