package com.sorobanquest.api.rest.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    @Bean
    fun sorobanQuestOpenApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Soroban Quest API")
                .version("v1")
                .description(
                    "Серверное API тренажёра ментальной арифметики: тренировки, опыт, " +
                        "достижения и таблица лидеров.",
                ),
        )
        .components(
            Components().addSecuritySchemes(
                "bearer-jwt",
                SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT"),
            ),
        )
}
