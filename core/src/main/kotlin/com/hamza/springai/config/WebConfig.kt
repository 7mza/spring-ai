package com.hamza.springai.config

import com.hamza.springai.shared.AssetManifestReader
import com.hamza.springai.shared.FileReader
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver
import org.thymeleaf.templateresolver.ITemplateResolver
import tools.jackson.databind.ObjectMapper

@Configuration
class WebConfig {
    @Bean
    fun assetManifestReader(
        resourceLoader: ResourceLoader,
        objectMapper: ObjectMapper,
    ): AssetManifestReader =
        AssetManifestReader(
            fileReader = FileReader(resourceLoader),
            objectMapper = objectMapper,
        )

    @Bean
    fun svgTemplateResolver(): ITemplateResolver =
        SpringResourceTemplateResolver().apply {
            prefix = "classpath:/static/svg/"
            suffix = ".svg"
            setTemplateMode("XML")
        }
}
