# 08 · javaService — миграции БД (db/migration)

Flyway: SQL в `src/main/resources/db/migration`, Java в `src/main/java/db/migration`.
`spring.jpa.hibernate.ddl-auto: validate` — схему меняет только Flyway.

## SQL-миграции

### V1__init_user.sql
Создаёт `user_entity(user_id PK IDENTITY, telegram_id BIGINT, name, nickname)`.

### V2__unique_telegram_id.sql
Добавляет `UNIQUE(telegram_id)` (`uk_user_entity_telegram_id`) — защита от двух записей при одновременных логинах.

### V3__user_identities.sql
Создаёт `user_identity(identity_id PK, user_id FK→user_entity ON DELETE CASCADE, provider, provider_user_id,
UNIQUE(provider, provider_user_id))`; переносит существующие `telegram_id` в `'TELEGRAM'`; удаляет
`telegram_id` и старый unique-констрейнт.

### V4__user_schedules.sql
Создаёт `user_schedules(scedule_id PK, user_id, title NOT NULL, note NOT NULL, date, time)` +
индекс `ix_user_schedules_user_date(user_id, date, time)`.

### V5__optional_schedule_text.sql
`title`/`note` → nullable (дата/время обязательны).

### V6__schedule_time_model.sql
Добавляет `start_at`, `end_at` (timestamptz), `timezone`, `status NOT NULL DEFAULT 'ACTIVE'`,
`source_channel`, `source_conversation_id`, `source_message_id`, `created_at`; `note` → VARCHAR(1000);
уникальный индекс `ux_user_schedules_source(source_channel, source_conversation_id, source_message_id)`.

### V8__messaging_tables.sql
Создаёт `processed_events(event_id, consumer, processed_at, PK(event_id, consumer))`,
`outbound_commands(command_id PK, type, channel, conversation_id, purpose, related_id, status,
external_message_id, created_at)` + индекс `ix_outbound_commands_related(related_id, purpose)`,
`reminders(id PK, schedule_id, kind, remind_at, status, attempts, claimed_at, sent_at,
UNIQUE(schedule_id, kind))`.

### V9__scheduled_deletions.sql
Создаёт `scheduled_deletions(id PK, channel, conversation_id, external_message_id, delete_at, status,
attempts, claimed_at, command_id, created_at, UNIQUE(channel, conversation_id, external_message_id))` +
индекс `ix_scheduled_deletions_due(status, delete_at)`.

### V10__user_timezone.sql
Добавляет `user_entity.timezone VARCHAR(64)` и `timezone_status VARCHAR(16)` (NULL — зона не определена).

## Java-миграции

### V7__BackfillScheduleStartAt (extends BaseJavaMigration)
#### `migrate(Context context)` → void
- **Назначение:** пересчитать старые записи (date+time) в `start_at` с зоной из placeholder `app-time-zone`.
- **Логика:** выбрать `scedule_id, date, time` где `start_at IS NULL`; для каждой вычислить
  `local.atZone(zone).toInstant()`; batch-`UPDATE start_at, timezone, status='ACTIVE', created_at=now`.
- **Почему Java:** одинаковая интерпретация DST у H2 и Postgres; `end_at` остаётся null.

### V11__ScheduleTimelessEntries (extends BaseJavaMigration)
#### `migrate(Context context)` → void
- **Назначение:** `ALTER TABLE user_schedules ALTER COLUMN time DROP NOT NULL` (записи «весь день»).
- **Почему Java:** единый стиль миграций (V7 тоже Java).