package com.sorobanquest.api.rest.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.sorobanquest.identity.api.AccessTokenVerifier
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import java.net.URI

/**
 * Конфигурация доступа: API без состояния (сессий нет), доступ по Bearer-токену.
 * Открытые маршруты — регистрация/вход, проверка здоровья и документация.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfiguration {

    @Bean
    fun jwtAuthenticationFilter(
        verifier: AccessTokenVerifier,
        objectMapper: ObjectMapper,
    ) = JwtAuthenticationFilter(verifier, objectMapper)

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtAuthenticationFilter: JwtAuthenticationFilter,
        objectMapper: ObjectMapper,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/api/v1/auth/**",
                    "/actuator/health",
                    "/actuator/health/**",
                    "/actuator/info",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                ).permitAll()
                it.anyRequest().authenticated()
            }
            .exceptionHandling { handling ->
                handling.authenticationEntryPoint { request, response, _ ->
                    writeProblem(
                        request,
                        response,
                        objectMapper,
                        HttpStatus.UNAUTHORIZED,
                        "UNAUTHORIZED",
                        "Требуется аутентификация",
                    )
                }
                handling.accessDeniedHandler { request, response, _ ->
                    writeProblem(
                        request,
                        response,
                        objectMapper,
                        HttpStatus.FORBIDDEN,
                        "FORBIDDEN",
                        "Недостаточно прав",
                    )
                }
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }

    private fun writeProblem(
        request: HttpServletRequest,
        response: HttpServletResponse,
        objectMapper: ObjectMapper,
        status: HttpStatus,
        code: String,
        detail: String,
    ) {
        val problem = ProblemDetail.forStatusAndDetail(status, detail).apply {
            title = status.reasonPhrase
            type = URI.create("https://sorobanquest.dev/errors/${code.lowercase()}")
            instance = URI.create(request.requestURI)
            setProperty("code", code)
        }
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        objectMapper.writeValue(response.outputStream, problem)
    }
}
