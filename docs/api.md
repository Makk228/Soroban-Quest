# Справочник API

Базовый URL: `http://localhost:8080/api/v1`
Живая документация: `http://localhost:8080/swagger-ui.html`

Все примеры ниже — реальные ответы запущенного приложения.

## Общие правила

* Формат — JSON (`Content-Type: application/json`), кодировка UTF-8.
* Все маршруты, кроме `/auth/**`, `/actuator/health` и документации, требуют
  заголовок `Authorization: Bearer <accessToken>`.
* Ошибки — RFC 7807 (`application/problem+json`) с дополнительным полем `code`.
* Каждому ответу присваивается `X-Request-Id` (можно прислать свой — он попадёт в логи).
* Даты — ISO-8601 в UTC.

| `code` | HTTP | Когда |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Некорректное тело или параметры запроса |
| `UNAUTHORIZED` | 401 | Нет токена, токен недействителен или истёк |
| `FORBIDDEN` | 403 | Ресурс принадлежит другому пользователю |
| `NOT_FOUND` | 404 | Объект не найден |
| `CONFLICT` | 409 | Email занят, лимит активных тренировок, параллельное изменение |
| `RULE_VIOLATION` | 422 | Нарушено бизнес-правило (например, порядок заданий) |

---

## Аутентификация

### Регистрация

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"anya@example.com","displayName":"Аня","password":"soroban2026"}'
```

`201 Created`

```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "accessTokenExpiresAt": "2026-10-22T20:08:50.597113629Z",
  "refreshToken": "GkTP99Af2j463gys9XahBrzSRV42b2sOIopT37sRTOo",
  "refreshTokenExpiresAt": "2026-11-21T19:38:50.597113629Z",
  "user": {
    "id": "a515de31-f82a-40e4-bcd0-83a456e15c57",
    "email": "anya@example.com",
    "displayName": "Аня",
    "createdAt": "2026-10-22T19:38:50.597113629Z"
  }
}
```

Требования: корректный email, имя 2–32 символа, пароль 8–72 символа минимум с
одной буквой и одной цифрой. Повторная регистрация email (в любом регистре) — `409`.

### Вход, обновление, выход

```bash
curl -X POST .../auth/login   -d '{"email":"anya@example.com","password":"soroban2026"}'
curl -X POST .../auth/refresh -d '{"refreshToken":"<refresh>"}'
curl -X POST .../auth/logout  -d '{"refreshToken":"<refresh>"}'   # 204 No Content
```

**Ротация:** после `refresh` прежний refresh-токен становится недействительным.
Если его предъявить повторно, сервер считает это кражей и отзывает **все**
refresh-токены пользователя (ответ `401`).

---

## Тренировки

### Каталог режимов

`GET /training/drills`

```json
[
  {
    "kind": "FLASH_ANZAN",
    "title": "Флеш-анзан",
    "description": "Числа вспыхивают одно за другим — удержи сумму в уме и назови результат.",
    "minDifficulty": 1,
    "maxDifficulty": 10,
    "recommendedTaskCount": 10
  }
]
```

Режимы: `FLASH_ANZAN`, `ADD_SUB_CHAIN`, `MULTIPLICATION`, `DIVISION`, `MIXED`.

### Начать тренировку

```bash
curl -X POST http://localhost:8080/api/v1/training/sessions \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"kind":"FLASH_ANZAN","difficulty":5,"taskCount":5}'
```

`201 Created`

```json
{
  "sessionId": "83eae387-1c26-4e65-a625-2bf25db79c73",
  "kind": "FLASH_ANZAN",
  "difficulty": 5,
  "status": "IN_PROGRESS",
  "taskCount": 5,
  "answeredCount": 0,
  "correctCount": 0,
  "accuracy": 0.0,
  "score": 0,
  "currentCombo": 0,
  "bestCombo": 0,
  "totalTimeMs": 0,
  "startedAt": "2026-10-22T19:38:50.730801992Z",
  "currentTask": {
    "index": 0,
    "kind": "FLASH_ANZAN",
    "prompt": { "type": "SEQUENCE", "numbers": [63, 31, 64, 34, 27], "flashIntervalMs": 1080 },
    "timeLimitMs": 26600
  }
}
```

Ограничения: `difficulty` 1–10, `taskCount` 5–50, одновременно не более
5 незавершённых тренировок (иначе `409`).

### Виды условий

| `prompt.type` | Поля | Что делает клиент |
|---|---|---|
| `SEQUENCE` | `numbers`, `flashIntervalMs` | Показывает числа по одному с интервалом; ответ — их сумма (отрицательные числа в цепочке означают вычитание) |
| `BINARY` | `left`, `right`, `operator` (`×` или `÷`) | Показывает пример целиком |

```json
{ "index": 0, "kind": "MULTIPLICATION",
  "prompt": { "type": "BINARY", "left": 661, "right": 43, "operator": "×" },
  "timeLimitMs": 25200 }
```

### Ответ на задание

```bash
curl -X POST http://localhost:8080/api/v1/training/sessions/$SESSION/answers \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"taskIndex":0,"answer":219,"elapsedMs":1800}'
```

`200 OK`

```json
{
  "correct": true,
  "givenAnswer": 219,
  "expectedAnswer": 219,
  "awardedPoints": 75,
  "combo": 1,
  "duplicate": false,
  "session": { "answeredCount": 1, "correctCount": 1, "score": 75, "status": "IN_PROGRESS", "...": "..." },
  "nextTask": {
    "index": 1,
    "kind": "FLASH_ANZAN",
    "prompt": { "type": "SEQUENCE", "numbers": [77, 32, 11, 89, 52], "flashIntervalMs": 1080 },
    "timeLimitMs": 26600
  }
}
```

* `taskIndex` обязан совпадать с `answeredCount`; иначе `422 RULE_VIOLATION`.
* Повторная отправка того же `taskIndex` возвращает сохранённый результат с
  `duplicate: true` — состояние не меняется (безопасные ретраи).
* После последнего задания `session.status` становится `COMPLETED`,
  `nextTask` отсутствует, начисляются опыт и достижения.

Пример ошибки порядка — `422`:

```json
{
  "type": "https://sorobanquest.dev/errors/rule_violation",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "Ожидается ответ на задание №1, получен №4",
  "instance": "/api/v1/training/sessions/83eae387-.../answers",
  "code": "RULE_VIOLATION",
  "expectedTaskIndex": 1,
  "receivedTaskIndex": 4,
  "rule": "task_out_of_order"
}
```

### Прочее по тренировкам

| Запрос | Ответ |
|---|---|
| `GET /training/sessions/{id}` | Состояние тренировки + текущее задание |
| `POST /training/sessions/{id}/abandon` | Статус `ABANDONED`; ответы больше не принимаются |
| `GET /training/sessions/{id}/answers` | Разбор: что ответил, что верно, сколько думал, сколько очков |
| `GET /training/sessions?page=0&size=20` | История: `items`, `page`, `size`, `totalItems`, `totalPages` |

```json
[
  { "taskIndex": 0, "givenAnswer": 219, "expectedAnswer": 219, "correct": true,
    "elapsedMs": 1800, "awardedPoints": 75, "answeredAt": "2026-10-22T19:38:50.740061Z" }
]
```

---

## Профиль и прогресс

### `GET /me`

```json
{
  "userId": "a515de31-f82a-40e4-bcd0-83a456e15c57",
  "displayName": "Аня",
  "email": "anya@example.com",
  "level": 3,
  "totalXp": 529,
  "xpIntoLevel": 229,
  "xpToNextLevel": 71,
  "currentStreakDays": 1,
  "longestStreakDays": 1,
  "lastTrainingDate": "2026-10-22",
  "totalSessions": 1,
  "totalTasks": 10,
  "totalCorrect": 9,
  "accuracy": 0.9,
  "dailyGoalXp": 100,
  "todayXp": 529,
  "dailyGoalReached": true
}
```

### `GET /me/achievements`

```json
[
  { "code": "FIRST_STEPS", "title": "Первые шаги",
    "description": "Завершить первую тренировку",
    "unlocked": true, "unlockedAt": "2026-10-22T19:40:12.113Z" },
  { "code": "PERFECT_RUN", "title": "Без единой ошибки",
    "description": "Пройти тренировку из 10+ заданий без ошибок", "unlocked": false }
]
```

### `GET /me/skills`

```json
[
  { "kind": "MULTIPLICATION", "sessions": 2, "tasksAnswered": 10, "correctAnswers": 10,
    "accuracy": 1.0, "bestScore": 310, "bestAverageAnswerMs": 1500,
    "updatedAt": "2026-10-22T19:41:02.551Z" }
]
```

### `GET /me/daily?days=7`

```json
[
  { "date": "2026-10-20", "xp": 0,   "sessions": 0, "goalReached": false },
  { "date": "2026-10-21", "xp": 180, "sessions": 2, "goalReached": true },
  { "date": "2026-10-22", "xp": 529, "sessions": 1, "goalReached": true }
]
```

### `PUT /me/daily-goal`

```bash
curl -X PUT http://localhost:8080/api/v1/me/daily-goal \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"dailyGoalXp":200}'
```

Допустимый диапазон — 20…5000 опыта, иначе `400`.

---

## Таблица лидеров

`GET /leaderboard?period=WEEKLY&limit=20` (`period`: `WEEKLY` | `ALL_TIME`, `limit` 1…100)

```json
{
  "period": "WEEKLY",
  "periodKey": "2026-W43",
  "entries": [
    { "rank": 1, "userId": "34c2...", "displayName": "Демо-ученик", "xp": 529, "sessions": 1, "currentUser": false },
    { "rank": 2, "userId": "0860...", "displayName": "Аня",         "xp": 310, "sessions": 1, "currentUser": true }
  ],
  "viewerEntry": { "rank": 2, "userId": "0860...", "displayName": "Аня", "xp": 310, "sessions": 1, "currentUser": true },
  "totalParticipants": 2
}
```

`viewerEntry` возвращается всегда, даже если пользователь не попал в топ.
`periodKey` для недели — ISO-неделя (`2026-W43`), для общего рейтинга — `ALL`.

---

## Служебные маршруты

| Маршрут | Назначение |
|---|---|
| `GET /actuator/health` | Проба готовности (используется healthcheck в Docker Compose) |
| `GET /v3/api-docs` | Спецификация OpenAPI в JSON |
| `GET /swagger-ui.html` | Интерактивная документация |

---

## Сквозной сценарий целиком

```bash
python3 server/scripts/demo.py            # регистрация → тренировка → профиль → рейтинг
```
