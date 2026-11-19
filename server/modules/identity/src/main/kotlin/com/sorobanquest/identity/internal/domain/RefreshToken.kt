package com.sorobanquest.identity.internal.domain

import com.sorobanquest.shared.model.UserId
import java.time.Instant
import java.util.UUID

/**
 * Refresh-токен хранится только в виде хеша: утечка таблицы не даёт возможности
 * войти под пользователем. При обновлении токен «ротируется» — старый помечается
 * использованным, а повторное предъявление старого токена трактуется как компрометация.
 */
internal data class RefreshToken(
    val id: UUID,
    val userId: UserId,
    val tokenHash: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val revokedAt: Instant?,
) {
    fun isActiveAt(moment: Instant): Boolean = revokedAt == null && moment.isBefore(expiresAt)
}
