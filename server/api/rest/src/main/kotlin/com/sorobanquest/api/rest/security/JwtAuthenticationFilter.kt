package com.sorobanquest.api.rest.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.sorobanquest.identity.api.AccessTokenVerifier
import com.sorobanquest.shared.error.AppException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.net.URI

/**
 * Проверяет Bearer-токен и кладёт пользователя в контекст запроса.
 * Сам формат токена фильтру неизвестен — он обращается к порту AccessTokenVerifier.
 */
class JwtAuthenticationFilter(
    private val verifier: AccessTokenVerifier,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response)
            return
        }

        val token = header.removePrefix(BEARER_PREFIX).trim()
        try {
            val user = verifier.verify(token)
            val authentication = UsernamePasswordAuthenticationToken(
                user,
                null,
                listOf(SimpleGrantedAuthority("ROLE_USER")),
            )
            SecurityContextHolder.getContext().authentication = authentication
        } catch (e: AppException) {
            SecurityContextHolder.clearContext()
            writeProblem(request, response, e.message ?: "Недействительный токен доступа")
            return
        }
        filterChain.doFilter(request, response)
    }

    private fun writeProblem(request: HttpServletRequest, response: HttpServletResponse, detail: String) {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detail).apply {
            title = HttpStatus.UNAUTHORIZED.reasonPhrase
            type = URI.create("https://sorobanquest.dev/errors/unauthorized")
            instance = URI.create(request.requestURI)
            setProperty("code", "UNAUTHORIZED")
        }
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        objectMapper.writeValue(response.outputStream, problem)
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}
