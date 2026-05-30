package com.hamza.springai.shared

data class AssetNotFoundException(
    private val name: String,
) : RuntimeException("asset $name not found at static/dist/asset-manifest.json")

internal class Exceptions
