# Архитектура Soroban Quest Server

Документ объясняет, как устроен сервер и почему принято именно такое устройство.

## 1. Общая форма: модульный монолит

Приложение разворачивается как один процесс и одна база данных, но внутри
разделено на модули с собственным доменом, хранилищем и публичным контрактом.

```
server/
├── shared/              типы и порты, общие для всех модулей
├── modules/
│   ├── identity/        аккаунты, пароли, токены
│   ├── training/        тренировки: генерация заданий, сессии, очки
│   ├── progression/     опыт, уровни, серии, достижения, статистика
│   └── leaderboard/     недельный и общий рейтинг
├── api/rest/            HTTP-слой: контроллеры, DTO, безопасность, OpenAPI
└── bootstrap/           composition root: Spring Boot, конфигурация, реализации портов
```

Каждый модуль — отдельный Gradle-проект. Зависимости объявлены явно, цикл между
модулями невозможен: Gradle его не соберёт.

### Как границы защищены компилятором

Внутри модуля публичен **только** пакет `api`. Всё остальное объявлено с
Kotlin-модификатором `internal`, который действует в пределах одного модуля
компиляции. Попытка обратиться из `progression` к `TrainingServiceImpl`
или к таблице `practice_sessions` просто не скомпилируется.

```kotlin
// training/api — видно всем
interface TrainingService { fun startSession(command: StartSessionCommand): SessionView }

// training/internal — невидимо снаружи модуля
internal class TrainingServiceImpl(...) : TrainingService
internal data class PracticeSession(...)
```

### Направление зависимостей

```
api:rest ──▶ identity.api, training.api, progression.api, leaderboard.api
leaderboard ──▶ progression.api (событие XpAwarded), identity.api (имена)
progression ──▶ training.api (событие и DrillKind)
identity, training ──▶ shared
bootstrap ──▶ всё (и только он знает про Spring-инфраструктуру)
```

`training` не знает ни о `progression`, ни о `leaderboard`: связь идёт
через события в обратную сторону.

## 2. Слои внутри модуля

```
api/                  контракт модуля: интерфейсы сервисов, команды, представления, события
internal/domain/      чистый Kotlin: агрегаты, политики, инварианты
internal/application/ сценарии: оркестрация домена, портов хранения и публикации событий
internal/persistence/ адаптеры к PostgreSQL (JDBC), реализуют порты application-слоя
internal/config/      @Configuration: сборка объектного графа модуля
```

Правила:

* **домен ничего не импортирует** из Spring, JDBC и HTTP — только `shared` и stdlib;
* **application** зависит от портов (`PracticeSessionRepository`, `TransactionRunner`,
  `DomainEventPublisher`), а не от их реализаций;
* **persistence** — единственное место с SQL;
* **config** — единственное место со Spring-аннотациями внутри модуля.

Пример: весь модуль `training` публикуется одним бином.

```kotlin
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TrainingProperties::class)
class TrainingModuleConfiguration {
    @Bean
    fun trainingService(jdbc: NamedParameterJdbcTemplate, /* ... */): TrainingService =
        TrainingServiceImpl(
            sessions = JdbcPracticeSessionRepository(jdbc),
            answers = JdbcAnswerRepository(jdbc),
            generators = TaskGeneratorRegistry(/* ... */),
            /* ... */
        )
}
```

Возвращается публичный тип `TrainingService`, аргументы конструктора —
`internal`-классы. Снаружи доступен только контракт.

## 3. Независимость от инфраструктуры

### От Spring

Домен и сценарии не используют `@Service`, `@Transactional` и `@EventListener`.
Вместо этого в `shared` объявлены порты:

```kotlin
interface TransactionRunner { fun <T> required(block: () -> T): T }
fun interface DomainEventPublisher { fun publish(event: DomainEvent) }
interface DomainEventHandler<E : DomainEvent> { val eventType: Class<E>; fun handle(event: E) }
```

Реализации — в `bootstrap`: `SpringTransactionRunner` (поверх `TransactionTemplate`)
и `SynchronousDomainEventBus`. В тестах порт транзакций подменяется одной строкой:

```kotlin
object DirectTransactionRunner : TransactionRunner {
    override fun <T> required(block: () -> T): T = block()
}
```

### От транспорта

Бизнес-логика не знает про HTTP. Контроллеры вызывают методы `*Service`, а
доменные ошибки несут `ErrorCode` без HTTP-статуса — отображение кода на статус
живёт в `GlobalExceptionHandler` REST-слоя. Чтобы добавить gRPC или GraphQL,
достаточно нового модуля рядом с `api:rest`; модули `training`, `progression`
и прочие не меняются.

### От базы данных

Application-слой работает с портами репозиториев. SQL сосредоточен в
`internal/persistence`. Используется JDBC, а не ORM: доменные классы остаются
без аннотаций, а блокировки и `on conflict` видны явно.

## 4. Взаимодействие модулей: события

Единственный способ связи «на запись» между модулями — доменные события.

```
training:    тренировка завершена  ──▶ TrainingSessionCompleted
progression: начисляет опыт, продлевает серию, выдаёт достижения ──▶ XpAwarded
leaderboard: увеличивает счётчики недельного и общего рейтинга
identity:    пользователь зарегистрирован ──▶ UserRegistered
progression: создаёт профиль ученика
```

Диспетчер (`SynchronousDomainEventBus`) собирает все бины `DomainEventHandler`
и вызывает подходящие **синхронно, в транзакции издателя**:

```kotlin
class SynchronousDomainEventBus(private val handlers: ObjectProvider<DomainEventHandler<*>>) : DomainEventPublisher {
    override fun publish(event: DomainEvent) {
        handlers.orderedStream().forEach { handler ->
            if (handler.eventType.isInstance(event)) {
                @Suppress("UNCHECKED_CAST")
                (handler as DomainEventHandler<DomainEvent>).handle(event)
            }
        }
    }
}
```

Почему синхронно: завершение тренировки, начисление опыта и обновление рейтинга
обязаны быть согласованными — либо всё, либо ничего. Асинхронная доставка
(очередь, outbox) добавляется заменой этой реализации, модули не затрагиваются.
`ObjectProvider` нужен, чтобы разорвать цикл бинов: `progression` одновременно
является подписчиком и издателем.

## 5. Ключевые доменные решения

### Детерминированная генерация заданий

Сессия хранит `seed`, `drill_kind`, `difficulty` и `generator_version`.
Задание с номером `N` всегда восстанавливается из `(seed, N)`:

```kotlin
fun randomFor(seed: Long, index: Int): Random = Random(seed + (index + 1L) * 1_000_003L)
```

Следствия:

* правильный ответ знает только сервер — клиент не может его подсмотреть;
* задания не хранятся в БД;
* результат воспроизводим: по той же сессии всегда получаются те же примеры;
* версия алгоритма сохранена, поэтому старые сессии остаются объяснимыми
  после изменения правил генерации.

### Правила предметной области в генераторах

* **флеш-анзан** — только сложение; количество слагаемых, разрядность и
  интервал показа зависят от сложности;
* **цепочка «плюс-минус»** — вычитание подставляется, только если промежуточная
  сумма остаётся положительной (правило соробана);
* **деление** строится «от ответа»: `делимое = делитель × частное`, остатка не бывает;
* **смешанный режим** выбирает делегата детерминированно из того же `Random`.

### Начисление очков и опыта разделено между модулями

`training` считает **очки за забег** (сложность × скорость × комбо),
`progression` переводит их в **опыт долгого прогресса** (очки / 2 + бонусы за
точность, безошибочность и длину серии). Это разные решения о разном, и живут
они в разных модулях.

### Серии

Правило в агрегате `LearnerProfile`: тренировка в тот же день серию не меняет,
на следующий день — продлевает, после пропуска — обнуляет. «День» определяется
в часовом поясе из конфигурации (`soroban.progression.time-zone`), а не в поясе
сервера.

## 6. Транзакции и конкурентность

| Сценарий | Защита |
|---|---|
| Два запроса отвечают на одно задание | PK `(session_id, task_index)` + `on conflict do nothing`; дубликат возвращает сохранённый результат с `duplicate: true` |
| Параллельные ответы в одной сессии | `select ... for update` на строке сессии + проверка `version` при обновлении |
| Две тренировки завершаются одновременно | `select ... for update` на строке профиля ученика |
| Начисление в дневную статистику и рейтинг | Инкремент через `on conflict do update` — без блокировок и потерь |
| Одновременная регистрация одного email | Уникальный индекс по `lower(email)` + `on conflict do nothing` → `409` |
| Повторное использование refresh-токена | Отзыв с условием `revoked_at is null`; выигрывает один запрос, остальные получают `401` и отзыв всех токенов |

Границы транзакций задаются явно в сценариях:

```kotlin
override fun submitAnswer(command: SubmitAnswerCommand): AnswerResult = transactions.required {
    val session = sessions.findByIdForUpdate(command.sessionId) ?: throw NotFoundException(...)
    // проверка владельца, идемпотентность, применение ответа, запись, событие
}
```

Поведение проверяется тестом `ConcurrencyIntegrationTest`: 8 одновременных
отправок одного ответа засчитываются один раз, 4 параллельно завершённые
тренировки не теряют опыт, а рейтинг совпадает с профилем.

## 7. База данных

Каждый модуль владеет своей схемой PostgreSQL; внешних ключей между схемами нет —
целостность между модулями обеспечивается их контрактами, а не БД. Это позволяет
вынести модуль в отдельный сервис, не переписывая остальные.

| Схема | Таблицы |
|---|---|
| `identity` | `users`, `refresh_tokens` |
| `training` | `practice_sessions`, `session_answers` |
| `progression` | `learner_profiles`, `achievements`, `skill_mastery`, `daily_progress` |
| `leaderboard` | `entries` |

Миграции Flyway лежат внутри модулей
(`modules/<модуль>/src/main/resources/db/migration/<модуль>`), нумерация версий
разведена по модулям: `1.x` — identity, `2.x` — training, `3.x` — progression,
`4.x` — leaderboard. Flyway применяет их при старте приложения.

## 8. Безопасность

* Пароли — BCrypt; политика длины и состава проверяется в домене.
* Access-токен — JWT HS256 с коротким сроком жизни; формат токена известен
  только классу `JwtAccessTokenIssuer`, остальной код работает через порт
  `AccessTokenVerifier`.
* Refresh-токен — 32 случайных байта; в БД хранится только SHA-256.
* Ротация: при обновлении старый токен отзывается; его повторное предъявление
  трактуется как компрометация и отзывает все сессии пользователя.
* Вход выполняет проверку пароля даже для несуществующего адреса — время ответа
  не выдаёт, зарегистрирован ли email.
* Секреты берутся только из переменных окружения; приложение не стартует,
  если `SOROBAN_JWT_SECRET` короче 32 байт.

## 9. Эксплуатация

* **Конфигурация** — `application.yml` с подстановкой переменных окружения;
  настройки каждого модуля изолированы префиксом (`soroban.training`,
  `soroban.identity`, ...) и типизированы через `@ConfigurationProperties`.
* **Логи** — через SLF4J; у каждого запроса есть `X-Request-Id`, который
  попадает в MDC и в заголовок ответа. События уровня `INFO`: регистрация,
  старт и завершение тренировки, начисление опыта, выдача достижения.
* **Здоровье** — `/actuator/health` (используется healthcheck в Docker Compose).
* **Запуск** — `docker compose up --build`: PostgreSQL + приложение,
  образ собирается многоступенчатым Dockerfile, работает от непривилегированного
  пользователя.

## 10. Тестовая стратегия

| Уровень | Что проверяется | Инфраструктура |
|---|---|---|
| Доменные тесты | Генераторы, очки, агрегат сессии, кривая уровней, серии, достижения, ключи недель | нет |
| Тесты сценариев | Аутентификация с настоящими BCrypt и JWT, подменены только хранилища | нет |
| Интеграционные | HTTP, валидация, коды ошибок, миграции, SQL, события между модулями | PostgreSQL в Testcontainers |
| Конкурентность | Идемпотентность ответов, отсутствие потерь опыта при гонках | PostgreSQL в Testcontainers |

Заглушки в продакшен-коде отсутствуют: тесты работают с настоящей БД и
настоящим HTTP-слоем.

## 11. Известные ограничения и развитие

* Доставка событий — внутрипроцессная и синхронная. При выделении модулей в
  отдельные сервисы понадобится outbox и брокер; порт `DomainEventPublisher`
  для этого уже есть.
* Рейтинг хранится агрегатами по периодам; историю прошлых недель можно
  показывать, но пока нет эндпоинта выбора произвольной недели.
* Нет ограничения частоты запросов (rate limiting) — код ошибки `RATE_LIMITED`
  зарезервирован.
* Сложность выбирает пользователь; адаптивный подбор по точности и скорости —
  следующий шаг развития.
