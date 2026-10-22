#!/usr/bin/env python3
"""
Демонстрационный сценарий: регистрация ученика, прохождение тренировки,
просмотр профиля, достижений и таблицы лидеров.

Зависимостей нет — только стандартная библиотека Python 3.
Запуск:  python3 scripts/demo.py [http://localhost:8080]
"""
import json
import sys
import urllib.error
import urllib.request
import uuid

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080"


def call(method, path, payload=None, token=None):
    data = json.dumps(payload).encode() if payload is not None else None
    request = urllib.request.Request(BASE + path, data=data, method=method)
    request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(request) as response:
            body = response.read().decode()
            return response.status, json.loads(body) if body else None
    except urllib.error.HTTPError as error:
        body = error.read().decode()
        return error.code, json.loads(body) if body else None


def solve(task):
    """Клиент решает задание сам — сервер правильный ответ заранее не присылает."""
    prompt = task["prompt"]
    if prompt["type"] == "SEQUENCE":
        return sum(prompt["numbers"])
    left, right = prompt["left"], prompt["right"]
    return left * right if prompt["operator"] == "×" else left // right


def main():
    email = f"demo-{uuid.uuid4()}@example.com"
    status, auth = call("POST", "/api/v1/auth/register", {
        "email": email,
        "displayName": "Демо-ученик",
        "password": "soroban2026",
    })
    assert status == 201, (status, auth)
    token = auth["accessToken"]
    print(f"1. Зарегистрирован {email}")

    status, drills = call("GET", "/api/v1/training/drills", token=token)
    print(f"2. Доступно тренировок: {len(drills)} — " + ", ".join(d["title"] for d in drills))

    status, session = call("POST", "/api/v1/training/sessions", {
        "kind": "FLASH_ANZAN", "difficulty": 6, "taskCount": 10,
    }, token=token)
    assert status == 201, (status, session)
    session_id = session["sessionId"]
    print(f"3. Начата тренировка {session_id} (флеш-анзан, сложность 6, 10 заданий)")

    task = session["currentTask"]
    for index in range(10):
        answer = solve(task)
        if index == 4:
            answer += 1  # намеренная ошибка, чтобы увидеть обнуление комбо
        status, result = call("POST", f"/api/v1/training/sessions/{session_id}/answers", {
            "taskIndex": index, "answer": answer, "elapsedMs": 1800,
        }, token=token)
        assert status == 200, (status, result)
        mark = "✓" if result["correct"] else "✗"
        print(f"   задание {index + 1:>2}: ответ {answer:<8} {mark}  +{result['awardedPoints']} очков, комбо {result['combo']}")
        task = result.get("nextTask")

    final = result["session"]
    print(f"4. Итог: {final['correctCount']}/{final['taskCount']} верно, очков {final['score']}, лучшее комбо {final['bestCombo']}")

    status, profile = call("GET", "/api/v1/me", token=token)
    print(f"5. Профиль: уровень {profile['level']}, опыт {profile['totalXp']} "
          f"(до следующего уровня {profile['xpToNextLevel']}), серия {profile['currentStreakDays']} дн.")

    status, achievements = call("GET", "/api/v1/me/achievements", token=token)
    unlocked = [a["title"] for a in achievements if a["unlocked"]]
    print(f"6. Достижения ({len(unlocked)} из {len(achievements)}): " + (", ".join(unlocked) or "пока нет"))

    status, board = call("GET", "/api/v1/leaderboard?period=WEEKLY&limit=5", token=token)
    print(f"7. Недельный рейтинг ({board['periodKey']}), участников {board['totalParticipants']}:")
    for entry in board["entries"]:
        marker = " <- вы" if entry["currentUser"] else ""
        print(f"   {entry['rank']}. {entry['displayName']}: {entry['xp']} опыта{marker}")

    status, repeated = call("POST", f"/api/v1/training/sessions/{session_id}/answers", {
        "taskIndex": 0, "answer": 0, "elapsedMs": 1000,
    }, token=token)
    print(f"8. Повторная отправка ответа на задание 1 -> HTTP {status}, duplicate={repeated['duplicate']} "
          f"(результат не изменился: {repeated['awardedPoints']} очков) — ретраи клиента безопасны")

    status, problem = call("POST", "/api/v1/training/sessions", {
        "kind": "FLASH_ANZAN", "difficulty": 42, "taskCount": 10,
    }, token=token)
    print(f"9. Старт тренировки со сложностью 42 -> HTTP {status}, код «{problem['code']}»: {problem['detail']}")

    print("\nГотово. Swagger UI: " + BASE + "/swagger-ui.html")


if __name__ == "__main__":
    main()
