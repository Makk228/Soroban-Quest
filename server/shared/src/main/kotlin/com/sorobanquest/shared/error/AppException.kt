package com.sorobanquest.shared.error

/**
 * Коды ошибок бизнес-слоя. Намеренно не содержат HTTP-статусов:
 * отображение кода на транспорт (HTTP/gRPC/GraphQL) — задача API-слоя.
 */
enum class ErrorCode {
    VALIDATION_FAILED,
    NOT_FOUND,
    CONFLICT,
    UNAUTHORIZED,
    FORBIDDEN,
    RULE_VIOLATION,
    RATE_LIMITED,
}

/** Базовое исключение прикладного уровня. */
sealed class AppException(
    val code: ErrorCode,
    message: String,
    val details: Map<String, Any?>,
    cause: Throwable?,
) : RuntimeException(message, cause)

class ValidationException(
    message: String,
    details: Map<String, Any?>,
) : AppException(ErrorCode.VALIDATION_FAILED, message, details, cause = null)

class NotFoundException(
    resource: String,
    id: Any?,
) : AppException(ErrorCode.NOT_FOUND, "$resource не найден: $id", mapOf("resource" to resource, "id" to id?.toString()), cause = null)

class ConflictException(
    message: String,
    details: Map<String, Any?>,
) : AppException(ErrorCode.CONFLICT, message, details, cause = null)

class UnauthorizedException(
    message: String,
) : AppException(ErrorCode.UNAUTHORIZED, message, details = emptyMap(), cause = null)

class ForbiddenException(
    message: String,
) : AppException(ErrorCode.FORBIDDEN, message, details = emptyMap(), cause = null)

/** Нарушение бизнес-правила домена (например, ответ на уже завершённую сессию). */
class RuleViolationException(
    val rule: String,
    message: String,
    details: Map<String, Any?>,
) : AppException(
        ErrorCode.RULE_VIOLATION,
        message,
        details + ("rule" to rule),
        cause = null,
    )
