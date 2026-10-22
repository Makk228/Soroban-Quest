# Soroban Quest — сервер

Серверная часть тренажёра ментальной арифметики. Полное описание проекта,
бизнес-логики и архитектуры — в [корневом README](../README.md) и
[`docs/architecture.md`](../docs/architecture.md).

## Быстрый старт

```bash
cp .env.example .env
# впишите свой SOROBAN_JWT_SECRET (минимум 32 байта)
docker compose up --build -d

python3 scripts/demo.py        # сквозной сценарий: регистрация → тренировка → профиль → рейтинг
```

* API — <http://localhost:8080/api/v1>
* Swagger UI — <http://localhost:8080/swagger-ui.html>
* Health — <http://localhost:8080/actuator/health>

## Сборка и тесты

```bash
./gradlew build                     # сборка + все тесты (нужен Docker для интеграционных)
./gradlew :modules:training:test    # только доменные тесты, без Docker
./gradlew :bootstrap:bootRun        # локальный запуск (нужны переменные окружения)
```

## Структура

| Каталог | Что внутри |
|---|---|
| `shared/` | Идентификаторы, ошибки, порты транзакций и событий |
| `modules/identity/` | Регистрация, вход, JWT, ротация refresh-токенов |
| `modules/training/` | Генерация заданий, сессии тренировок, очки |
| `modules/progression/` | Опыт, уровни, серии, достижения, статистика |
| `modules/leaderboard/` | Недельный и общий рейтинг |
| `api/rest/` | Контроллеры, DTO, валидация, обработка ошибок, OpenAPI |
| `bootstrap/` | Spring Boot, конфигурация, реализации портов, интеграционные тесты |
| `scripts/demo.py` | Демонстрационный сценарий без зависимостей |
