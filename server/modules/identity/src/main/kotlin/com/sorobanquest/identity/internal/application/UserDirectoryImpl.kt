package com.sorobanquest.identity.internal.application

import com.sorobanquest.identity.api.UserDirectory
import com.sorobanquest.identity.api.UserSummary
import com.sorobanquest.identity.internal.domain.UserAccount
import com.sorobanquest.shared.model.UserId

internal class UserDirectoryImpl(
    private val users: UserRepository,
) : UserDirectory {

    override fun findById(id: UserId): UserSummary? = users.findById(id)?.toSummary()

    override fun findAllByIds(ids: Collection<UserId>): Map<UserId, UserSummary> =
        if (ids.isEmpty()) {
            emptyMap()
        } else {
            users.findAllByIds(ids).associate { it.id to it.toSummary() }
        }
}

private fun UserAccount.toSummary() =
    UserSummary(id, email.value, displayName, createdAt)
