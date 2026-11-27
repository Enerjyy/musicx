# План документирования кодовой базы Musicx (RU)

## Цель
Описать устройство **каждой функции** прод-кода монорепозитория Musicx:
`javaService` (ядро), `javaMusicxTG` (транспорт Telegram), `tsReactMainPage/musicx` (frontend).
Без тестов и без сгенерированного `src/api/generated/api.ts`.
Глубина — максимальная (назначение, параметры, возврат, побочные эффекты, ошибки, связи).

## Архитектура (кратко)
Монорепо, event-driven, 3 деплоя (docker-compose):
- `javaService` (:8082) — ядро: REST, PostgreSQL/Flyway, Kafka, LLM (OpenRouter), напоминания.
- `javaMusicxTG` (:8083) — транспорт Telegram: приём/отправка, дедупликация.
- `frontend` (:3000) — React/Vite SPA (nginx).

Связь сервисов — Kafka. Контракт v1/v2: `inbound.messages`, `outbound.commands.telegram`,
`outbound.results`, у каждого `.dlt`. Сообщения — immutable records с валидацией в конструкторе.

## Ключевые потоки
1. Auth: `initData` → `TGDataValidation` (HMAC) → `userService.upsertByIdentity` → JWT.
2. Создание: `MusicxBot` → Kafka → `InboundMessageConsumer` → `CreateScheduleUseCase` →
   `LlmEventParser` → `ScheduleValidator` → транзакция → подтверждение после коммита.
3. Перенос/удаление: `ModifyScheduleUseCase` (нечёткий матчинг, уточнения).
4. Результаты: `OutboundResultsConsumer` → статусы, отложенные удаления, ретраи.
5. Напоминания/удаления: `ReminderProcessor`, `ScheduledDeletionProcessor` (`@Scheduled`).
6. Frontend: `http.ts`/`token.ts`/`auth.ts`/`session.ts`, `useTelegram`, страницы.

## Структура результата
```
docs/codebase/
  PLAN.ru.md  PLAN.en.md
  00-overview.md
  01-javaService/ 01-app-config.md 02-security.md 03-user-domain.md 04-api.md
                  05-llm.md 06-schedule-logic.md 07-messaging.md 08-migrations.md
  02-javaMusicxTG/ 01-app-config.md 02-telegram-bot.md 03-telegram-commands.md
                    04-gateway.md 05-messaging.md
  03-frontend/     01-bootstrap.md 02-api-layer.md 03-telegram.md 04-audio.md
                    05-calendar.md 06-metronome.md 07-tuner.md 08-pages.md
```

## Шаблон описания функции
```
#### `имя(параметры)` → тип
- Назначение: ...
- Параметры: ... (тип, смысл, nullability)
- Возврат: ...
- Побочные эффекты: БД / Kafka / сеть / файлы
- Граничные случаи и ошибки: ...
- Вызывается из / вызывает: ...
- Инварианты/идемпотентность: ...
```
Для record — поля и валидация; для enum — значения и применение; для контроллеров — маршрут, коды.

## Порядок выполнения
1. `00-overview.md`.
2. Модули `01`→`03` по порядку, файл за файлом.
3. Работа партиями (объём ~115 файлов кода), сводка прогресса после каждой партии.
4. Проверка покрытия: grep по именам файлов main-деревьев.

## Принятые умолчания
- Тесты и `src/api/generated/api.ts` исключены.
- CSS/JSON/изображения не разбираются построчно.
- Язык документации — русский; идентификаторы — как в коде.