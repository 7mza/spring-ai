package com.hamza.springai.evaluation

import org.springframework.web.bind.annotation.RestController

@RestController
class EvalCtrl(
    private val service: IEvalService,
) : IEvalApi {
    override fun eval(request: EvalRequest): EvalResponse = service.eval(request)
}
