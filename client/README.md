# Клиент Soroban Quest

Каталог зарезервирован под клиентское приложение (лабораторная работа №2).

Планируемый стек: **Kotlin Multiplatform / Jetpack Compose** (Android как основная
платформа) поверх REST API из `../server`.

## Экраны, под которые уже готов сервер

| Экран | Эндпоинты |
|---|---|
| Регистрация / вход | `POST /api/v1/auth/register`, `/login`, `/refresh`, `/logout` |
| Выбор тренировки | `GET /api/v1/training/drills` |
| Игровой экран | `POST /api/v1/training/sessions`, `POST .../answers`, `POST .../abandon` |
| Итоги и разбор | `GET /api/v1/training/sessions/{id}/answers` |
| Профиль и прогресс | `GET /api/v1/me`, `/me/skills`, `/me/daily`, `PUT /me/daily-goal` |
| Достижения | `GET /api/v1/me/achievements` |
| Рейтинг | `GET /api/v1/leaderboard` |
| История тренировок | `GET /api/v1/training/sessions` |

## Важное для клиента

* Правильные ответы сервер **не присылает заранее** — только после ответа на задание.
* Повторная отправка ответа на тот же `taskIndex` безопасна (идемпотентна),
  в ответе придёт `duplicate: true` — это упрощает ретраи при плохой сети.
* Задание приходит в поле `currentTask`/`nextTask`; `prompt.type` = `SEQUENCE`
  (числа для флеш-анзана и цепочек) либо `BINARY` (умножение и деление).
* `flashIntervalMs` — с какой скоростью показывать числа, `timeLimitMs` — мягкий
  лимит на ответ.
* Access-токен живёт 30 минут, refresh — 30 дней с ротацией: после `refresh`
  старый refresh-токен становится недействительным, его нужно заменить в хранилище.
