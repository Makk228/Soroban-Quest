package com.sorobanquest.shared.model

import java.util.UUID

/**
 * Идентификатор пользователя. Объявлен в shared, а не в модуле identity,
 * чтобы training/progression/leaderboard могли ссылаться на пользователя,
 * не завися от модуля аутентификации.
 */
@JvmInline
value class UserId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun parse(raw: String): UserId =
            UserId(runCatching { UUID.fromString(raw) }.getOrElse { error("Некорректный UserId: $raw") })
    }
}

/** Идентификатор тренировочной сессии. */
@JvmInline
value class SessionId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun parse(raw: String): SessionId =
            SessionId(runCatching { UUID.fromString(raw) }.getOrElse { error("Некорректный SessionId: $raw") })
    }
}
