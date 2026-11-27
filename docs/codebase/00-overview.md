# Musicx — обзор кодовой базы

> Документация покрывает прод-код: `javaService`, `javaMusicxTG`, `tsReactMainPage/musicx`.
> Тесты и сгенерированный `src/api/generated/api.ts` не разбираются построчно.

## 1. Что это

Монорепозиторий `Musicx` — event-driven приложение для музыкантов: метроном, тюнер,
календарь-дневник, интеграция с Telegram Mini App. Сборка: Gradle (Kotlin DSL) для Java,
Vite + React + TypeScript для фронта, Docker Compose для локального запуска.

## 2. Состав репозитория

| Каталог | Роль | Стек |
|---|---|---|
| `javaService/` | Ядро: REST API, БД, Kafka, LLM-парсинг, напоминания, отложенные удаления | Java 21, Spring Boot 4.1, Spring Data JPA, Flyway, PostgreSQL, spring-kafka, jjwt |
| `javaMusicxTG/` | Транспорт Telegram: приём сообщений, отправка, дедупликация | Java 21, Spring Boot, telegrambots-spring-boot-starter, spring-kafka |
| `tsReactMainPage/musicx/` | Frontend SPA | React 19, TypeScript, Vite, Tone.js, Orval |
| `docker-compose.yml` | Локальная инфраструктура | Postgres 16, Kafka 7.5 (+zookeeper) |

Корневой `settings.gradle.kts` включает только `javaMusicxTG` и `javaService`; фронт собирается отдельно (Vite).

## 3. Топология развёртывания (docker-compose)

| Сервис | Контейнер | Порт | Назначение |
|---|---|---|---|
| `db` | postgres:16-alpine | 5433→5432 | БД, healthcheck `pg_isready` |
| `zookeeper` | cp-zookeeper:7.5.0 | 2181 | Координатор Kafka |
| `kafka` | cp-kafka:7.5.0 | 9092 | Брокер, listeners PLAINTEXT + INTERNAL |
| `service-one` | `javaService` | 8082→8080 | Ядро; ждёт healthy db+kafka |
| `service-two` | `javaMusicxTG` | 8083→8080 | Транспорт Telegram; ждёт healthy db+kafka |
| `frontend` | `tsReactMainPage/musicx` (nginx) | 3000→80 | SPA, `VITE_API_URL=/api` |

Переменные окружения: `DB_*`, `BOT_NAME`, `BOT_TOKEN`, `JWT_SECRET`, `OPENROUTER_API_KEY`,
`CORS_ALLOWED_ORIGINS`. Kafka для обоих Java-сервисов: `kafka:29092` (внутренний listener).

## 4. Контракт обмена (Kafka)

Топики (класс `Topics` в обоих сервисах, копии синхронны):

- `inbound.messages` — транспорт → ядро: `IncomingMessage`.
- `outbound.commands.telegram` — ядро → транспорт: `ShowMessage`/`SendMessage`/`DeleteMessage`/`ShowProgress`.
- `outbound.results` — транспорт → ядро: `DeliveryResult`.
- `<topic>.dlt` — DLT для каждого топика.

Сообщения — immutable `record`, реализующие sealed-интерфейс `KafkaMessage`
(разрешены `IncomingMessage`, `OutboundCommand`, `DeliveryResult`). Общие поля конверта:
`eventId`, `schemaVersion` (сейчас 1), `occurredAt` (ISO-8601 UTC), `channel`, `conversationId`.
Ключ партиции — `channel + ":" + conversationId` (порядок внутри диалога).
Валидация — в каноническом конструкторе через `Envelope`.

`OutboundCommand` (sealed) = `ShowProgress` | `SendMessage` | `DeleteMessage`.

## 5. Сквозные сценарии

### 5.1 Авторизация
`POST /auth/telegram` (`AuthController.login`) → `TGDataValidation.validate(initData, maxAge)`
(проверка HMAC-SHA256 по алгоритму Telegram + `auth_date`) → `userService.upsertByIdentity(TELEGRAM, id, …)`
→ `JwtService.issue(userId, TELEGRAM)` → `AuthResponse{token, expiresIn, user}`.
Далее каждый запрос несёт `Authorization: Bearer <jwt>`; `JwtAuthFilter` валидирует и кладёт
`UserPrincipal` в request attribute.

### 5.2 Создание записи
`MusicxBot.onUpdateReceived` (личный чат, есть текст) → черновик «Thinking…» (`sendMessageDraft`) →
`IncomingMessage` в `inbound.messages` → `InboundMessageConsumer.handle` → `CreateScheduleUseCase.handle`:
1. дедуп по тройке (channel, conversation, messageId) → повтор подтверждения без LLM;
2. прогресс «Разбираю…» → `LlmEventParser.parse` (OpenRouter, fallback-модели, бюджет 25 с);
3. не-CREATE интент → `ModifyScheduleUseCase.handle`;
4. «Проверяю…» → `ScheduleValidator.validate` → `ValidatedSchedule` либо отказ;
5. «Сохраняю…» → одна транзакция: `processed_events` + `userSchedules` + `Reminder` + `CommandJournalEntry`;
6. подтверждение публикуется строго после коммита (`TransactionSynchronization.afterCommit`).

### 5.3 Перенос/удаление
`ModifyScheduleUseCase.handle`: дедуп по eventId → (для UPDATE) валидация нового времени →
поиск кандидатов за день (`findByUserIdAndStatusAndDate`, ACTIVE) → нечёткий фильтр по времени (±30 мин)
и подстроке названия → 0 или >1 кандидатов = уточняющий вопрос (`CLARIFY_SCHEDULE`); ровно 1 = транзакция
(обновление/CANCELED + пересоздание напоминаний + журнал) → подтверждение после коммита.

### 5.4 Результаты доставки
`OutboundResultsConsumer.handle` → `apply`: обновляет статус в `CommandJournalEntry`
(SENT/ALREADY_GONE → SENT; BLOCKED/FAILED → FAILED; RATE_LIMITED/TRANSIENT → без изменений) и
по `purpose`: `CONFIRM_SCHEDULE` → задача `ScheduledDeletion`; `DELETE_CONFIRMATION` → статус задачи;
`REMINDER` → статус напоминания (с ретраями до `max-attempts`).

### 5.5 Напоминания и отложенные удаления
`ReminderProcessor.processDue` (`@Scheduled` cron, по умолчанию каждый час): возвращает зависшие
SENDING→PENDING, гасит EXPIRED, захватывает due-напоминания (`lockDue`, PESSIMISTIC_WRITE + SKIP LOCKED)
→ SENDING, публикует `SendMessage` после коммита.
`ScheduledDeletionProcessor.processDue` (`@Scheduled` fixedDelay 5 c): возвращает зависшие, захватывает
просроченные PENDING → SENDING, публикует `DeleteMessage` после коммита.

### 5.6 Часовой пояс
Mini App при логине шлёт `PUT /users/me/timezone` (статус AUTO) — `usersController.setMyTimezone`.
Команда `/timezone [zone]` в чате — `TimezoneCommandHandler` (статус MANUAL, не перетирается AUTO).

### 5.7 Frontend
`main.tsx`: `installAudioUnlock()` (разблокировка AudioContext) + `installSession()` (логин по initData,
реакция на 401) → `App` с вкладками Tuner/Metronome/Calendar. `http.ts` — fetch-обёртка с JWT и
одним ретраем на 401; `token.ts` — токен в памяти; `useTelegram` — fullscreen/insets Mini App.

## 6. Ключевые инварианты

- **Идемпотентность**: `processed_events(event_id, consumer)` + уникальный индекс
  `ux_user_schedules_source` + уникальность `reminders(schedule_id, kind)` и
  `scheduled_deletions(channel, conversation_id, external_message_id)`.
- **Порядок**: ключ партиции = диалог; отправка наружу только после коммита.
- **Время**: модель возвращает только локальные значения; `Instant` считает Java (`atZone`), DST — на java.time.
- **Безопасность**: `userId` всегда из токена (`@RequestAttribute`), не из параметров; initData проверяется до БД.
- **Канал-агностичность**: ядро не знает про Telegram; команды публикуются через `OutboundChannel`/`ChannelRegistry`.

## 7. Навигация по документации

- `01-javaService/` — ядро (config, security, user, api, llm, schedule, messaging, migrations).
- `02-javaMusicxTG/` — транспорт Telegram.
- `03-frontend/` — React-приложение.