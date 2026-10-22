package com.sorobanquest.bootstrap.events

import com.sorobanquest.shared.event.DomainEvent
import com.sorobanquest.shared.event.DomainEventHandler
import com.sorobanquest.shared.event.DomainEventPublisher
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.ObjectProvider

private val log = KotlinLogging.logger {}

/**
 * Внутрипроцессная шина доменных событий.
 *
 * Обработчики вызываются синхронно и в той же транзакции, что и издатель:
 * завершение тренировки, начисление опыта и обновление рейтинга либо происходят
 * целиком, либо откатываются вместе. Обработчики получаются ленивым провайдером,
 * чтобы модуль-подписчик мог сам публиковать события (progression -> leaderboard).
 *
 * Если в будущем понадобится асинхронная доставка (очередь, outbox), заменится
 * только эта реализация — модули зависят от порта DomainEventPublisher.
 */
class SynchronousDomainEventBus(
    private val handlers: ObjectProvider<DomainEventHandler<*>>,
) : DomainEventPublisher {

    override fun publish(event: DomainEvent) {
        var delivered = 0
        handlers.orderedStream().forEach { handler ->
            if (handler.eventType.isInstance(event)) {
                @Suppress("UNCHECKED_CAST")
                (handler as DomainEventHandler<DomainEvent>).handle(event)
                delivered++
            }
        }
        log.debug { "Событие ${event.javaClass.simpleName} доставлено обработчикам: $delivered" }
    }
}
