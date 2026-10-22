package com.sorobanquest.api.rest.error

import com.sorobanquest.shared.error.AppException
import com.sorobanquest.shared.error.ErrorCode
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.net.URI

private val log = KotlinLogging.logger {}

/**
 * Единая обработка ошибок: все ответы отдаются в формате RFC 7807 (application/problem+json).
 *
 * Бизнес-слой бросает доменные исключения с кодом ошибки, а HTTP-статус выбирается
 * здесь. Благодаря этому домен не зависит от протокола, а клиент получает
 * предсказуемую структуру ответа.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(AppException::class)
    fun handleAppException(exception: AppException, request: HttpServletRequest): ProblemDetail {
        val status = exception.code.toHttpStatus()
        if (status.is5xxServerError) {
            log.error(exception) { "Ошибка обработки ${request.method} ${request.requestURI}" }
        } else {
            log.debug { "${request.method} ${request.requestURI} -> ${status.value()} ${exception.code}: ${exception.message}" }
        }
        return problem(status, exception.code.name, exception.message ?: status.reasonPhrase, request).apply {
            exception.details.forEach { (key, value) -> setProperty(key, value) }
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(exception: MethodArgumentNotValidException, request: HttpServletRequest): ProblemDetail {
        val fieldErrors = exception.bindingResult.fieldErrors.associate {
            it.field to (it.defaultMessage ?: "некорректное значение")
        }
        return problem(
            status = HttpStatus.BAD_REQUEST,
            code = ErrorCode.VALIDATION_FAILED.name,
            detail = "Запрос не прошёл валидацию",
            request = request,
        ).apply { setProperty("fieldErrors", fieldErrors) }
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(exception: HttpMessageNotReadableException, request: HttpServletRequest): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED.name, "Некорректное тело запроса", request)

    @ExceptionHandler(MethodArgumentTypeMismatchException::class, MissingServletRequestParameterException::class)
    fun handleBadParameter(exception: Exception, request: HttpServletRequest): ProblemDetail =
        problem(
            HttpStatus.BAD_REQUEST,
            ErrorCode.VALIDATION_FAILED.name,
            exception.message ?: "Некорректный параметр запроса",
            request,
        )

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception, request: HttpServletRequest): ProblemDetail {
        log.error(exception) { "Непредвиденная ошибка при обработке ${request.method} ${request.requestURI}" }
        return problem(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_ERROR",
            "Внутренняя ошибка сервера",
            request,
        )
    }

    private fun problem(status: HttpStatus, code: String, detail: String, request: HttpServletRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            title = status.reasonPhrase
            type = URI.create("https://sorobanquest.dev/errors/${code.lowercase()}")
            instance = URI.create(request.requestURI)
            setProperty("code", code)
        }
}

internal fun ErrorCode.toHttpStatus(): HttpStatus = when (this) {
    ErrorCode.VALIDATION_FAILED -> HttpStatus.BAD_REQUEST
    ErrorCode.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED
    ErrorCode.FORBIDDEN -> HttpStatus.FORBIDDEN
    ErrorCode.NOT_FOUND -> HttpStatus.NOT_FOUND
    ErrorCode.CONFLICT -> HttpStatus.CONFLICT
    // 422: запрос синтаксически корректен, но нарушает правило предметной области
    ErrorCode.RULE_VIOLATION -> HttpStatus.UNPROCESSABLE_ENTITY
    ErrorCode.RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS
}
