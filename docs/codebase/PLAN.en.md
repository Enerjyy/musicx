# Musicx Codebase Documentation Plan (EN)

## Goal
Describe the structure of **every function** in the production code of the Musicx monorepo:
`javaService` (core), `javaMusicxTG` (Telegram transport), `tsReactMainPage/musicx` (frontend).
Excludes tests and the generated `src/api/generated/api.ts`.
Depth — maximum (purpose, parameters, return, side effects, errors, call relations).

## Architecture (short)
Monorepo, event-driven, 3 deployments (docker-compose):
- `javaService` (:8082) — core: REST, PostgreSQL/Flyway, Kafka, LLM (OpenRouter), reminders.
- `javaMusicxTG` (:8083) — Telegram transport: receive/send, deduplication.
- `frontend` (:3000) — React/Vite SPA (nginx).

Services communicate via Kafka. Contract v1/v2: `inbound.messages`, `outbound.commands.telegram`,
`outbound.results`, each with `.dlt`. Messages are immutable records validated in the constructor.

## Key flows
1. Auth: `initData` → `TGDataValidation` (HMAC) → `userService.upsertByIdentity` → JWT.
2. Create: `MusicxBot` → Kafka → `InboundMessageConsumer` → `CreateScheduleUseCase` →
   `LlmEventParser` → `ScheduleValidator` → transaction → confirmation after commit.
3. Modify/delete: `ModifyScheduleUseCase` (fuzzy matching, clarifications).
4. Results: `OutboundResultsConsumer` → statuses, scheduled deletions, retries.
5. Reminders/deletions: `ReminderProcessor`, `ScheduledDeletionProcessor` (`@Scheduled`).
6. Frontend: `http.ts`/`token.ts`/`auth.ts`/`session.ts`, `useTelegram`, pages.

## Result layout
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

## Function description template
```
#### `name(parameters)` → type
- Purpose: ...
- Parameters: ... (type, meaning, nullability)
- Return: ...
- Side effects: DB / Kafka / network / files
- Edge cases and errors: ...
- Called from / calls: ...
- Invariants/idempotency: ...
```
For records — fields and validation; for enums — values and usage; for controllers — route, status codes.

## Execution order
1. `00-overview.md`.
2. Modules `01`→`03` in order, file by file.
3. Work in batches (~115 code files), progress summary after each batch.
4. Coverage check: grep by main-tree file names.

## Defaults
- Tests and `src/api/generated/api.ts` excluded.
- CSS/JSON/images not analyzed line by line.
- Documentation language — Russian; identifiers as in code.