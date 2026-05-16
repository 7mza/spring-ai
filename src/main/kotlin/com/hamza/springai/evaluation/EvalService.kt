package com.hamza.springai.evaluation

import org.springframework.ai.evaluation.Evaluator
import org.springframework.stereotype.Service

interface IEvalService {
    fun eval(request: EvalRequest): EvalResponse
}

@Service
class EvalService(
    private val evaluator: Evaluator,
) : IEvalService {
    override fun eval(request: EvalRequest): EvalResponse =
        evaluator
            .evaluate(request.toEvaluationRequest())
            .toEvalResponse(
                prompt = request.prompt,
                response = request.response,
            )
}
