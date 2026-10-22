package com.sorobanquest.training.internal.config

import com.sorobanquest.shared.event.DomainEventPublisher
import com.sorobanquest.shared.id.IdGenerator
import com.sorobanquest.shared.tx.TransactionRunner
import com.sorobanquest.training.api.TrainingService
import com.sorobanquest.training.internal.application.SeedGenerator
import com.sorobanquest.training.internal.application.TrainingServiceImpl
import com.sorobanquest.training.internal.application.TrainingSettings
import com.sorobanquest.training.internal.domain.generator.AddSubChainGenerator
import com.sorobanquest.training.internal.domain.generator.DivisionGenerator
import com.sorobanquest.training.internal.domain.generator.FlashAnzanGenerator
import com.sorobanquest.training.internal.domain.generator.MixedGenerator
import com.sorobanquest.training.internal.domain.generator.MultiplicationGenerator
import com.sorobanquest.training.internal.domain.generator.TaskGeneratorRegistry
import com.sorobanquest.training.internal.persistence.JdbcAnswerRepository
import com.sorobanquest.training.internal.persistence.JdbcPracticeSessionRepository
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.security.SecureRandom
import java.time.Clock

/** Настройки модуля тренировок (префикс `soroban.training` в конфигурации). */
@ConfigurationProperties(prefix = "soroban.training")
data class TrainingProperties(
    /** Сколько незавершённых тренировок пользователь может держать одновременно. */
    val maxActiveSessions: Int = 5,
    val minTaskCount: Int = 5,
    val maxTaskCount: Int = 50,
)

/**
 * Композиционный корень модуля: единственное место, где модуль соприкасается со Spring.
 * Наружу публикуется только `TrainingService`, все внутренние классы остаются `internal`.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TrainingProperties::class)
class TrainingModuleConfiguration {

    @Bean
    fun trainingService(
        jdbc: NamedParameterJdbcTemplate,
        transactionRunner: TransactionRunner,
        eventPublisher: DomainEventPublisher,
        clock: Clock,
        idGenerator: IdGenerator,
        properties: TrainingProperties,
    ): TrainingService {
        val flash = FlashAnzanGenerator()
        val chain = AddSubChainGenerator()
        val multiplication = MultiplicationGenerator()
        val division = DivisionGenerator()
        val registry = TaskGeneratorRegistry(
            listOf(
                flash,
                chain,
                multiplication,
                division,
                MixedGenerator(listOf(flash, chain, multiplication, division)),
            ),
        )
        val secureRandom = SecureRandom()
        return TrainingServiceImpl(
            sessions = JdbcPracticeSessionRepository(jdbc),
            answers = JdbcAnswerRepository(jdbc),
            generators = registry,
            transactions = transactionRunner,
            events = eventPublisher,
            clock = clock,
            ids = idGenerator,
            seeds = SeedGenerator { secureRandom.nextLong() },
            settings = TrainingSettings(
                maxActiveSessions = properties.maxActiveSessions,
                minTaskCount = properties.minTaskCount,
                maxTaskCount = properties.maxTaskCount,
            ),
        )
    }
}
