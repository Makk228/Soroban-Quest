package com.sorobanquest.shared.event

import java.time.Instant

/** Базовый контракт доменного события. */
interface DomainEvent {
    val occurredAt: Instant
}

/**
 * Порт публикации событий. Модуль-источник не знает, кто его слушает,
 * поэтому связность между модулями остаётся односторонней.
 */
fun interface DomainEventPublisher {
    fun publish(event: DomainEvent)
}

/**
 * Порт обработчика события. Реализации объявляются внутри модулей-подписчиков,
 * диспетчеризация — в композиционном корне (bootstrap).
 */
interface DomainEventHandler<E : DomainEvent> {
    val eventType: Class<E>
    fun handle(event: E)
}
