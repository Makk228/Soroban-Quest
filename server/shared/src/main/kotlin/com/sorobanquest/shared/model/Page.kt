package com.sorobanquest.shared.model

import com.sorobanquest.shared.error.ValidationException

/** Запрос постраничной выдачи. Валидация выполняется в конструкторе. */
data class PageRequest(val page: Int, val size: Int) {
    init {
        if (page < 0) throw ValidationException("Номер страницы не может быть отрицательным")
        if (size !in 1..MAX_SIZE) throw ValidationException("Размер страницы должен быть в диапазоне 1..$MAX_SIZE")
    }

    val offset: Int get() = page * size

    companion object {
        const val MAX_SIZE = 100
        fun of(page: Int?, size: Int?): PageRequest = PageRequest(page ?: 0, size ?: 20)
    }
}

/** Страница результатов. */
data class Page<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
) {
    val totalPages: Int get() = if (size == 0) 0 else ((totalItems + size - 1) / size).toInt()

    fun <R> map(transform: (T) -> R): Page<R> = Page(items.map(transform), page, size, totalItems)
}
