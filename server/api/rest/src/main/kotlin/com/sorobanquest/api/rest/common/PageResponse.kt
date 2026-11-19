package com.sorobanquest.api.rest.common

import com.sorobanquest.shared.model.Page

/** Транспортное представление страницы. */
data class PageResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
)

object PageResponseFactory {
    fun <S, T> of(
        page: Page<S>,
        transform: (S) -> T,
    ) = PageResponse(
        items = page.items.map(transform),
        page = page.page,
        size = page.size,
        totalItems = page.totalItems,
        totalPages = page.totalPages,
    )
}
