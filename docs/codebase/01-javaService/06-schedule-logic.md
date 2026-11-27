# 06 · javaService — логика расписания (Logic/schedule)

Пакет `enerjy.musicxService.Logic.schedule` (+ `parse`). Сценарии создания/изменения, валидатор, парсинг.

## CreateScheduleUseCase (`@Service`)
Сценарий «пользователь прислал текст — создана запись». `CONSUMER_ID = "musicx-core-inbound"`.

#### Конструкторы
- Публичный `(eventParser, validator, modifySchedule, schedulesRepo, userService, processedEvents, reminders,
  commandJournal, channels, transactions, timeZone, reminderOffset)` — делегирует в package-private с `Clock.systemUTC()`.
- Package-private с явными `Clock`, `ZoneId`, `Duration` — для тестов.
- **Ошибки:** `IllegalArgumentException` если `reminderOffset` не положителен.

#### `handle(IncomingMessage message)` → void
- **Назначение:** главный вход сценария создания.
- **Логика:**
  1. `channels.by(channel)`; поиск по тройке источника → если есть, `resendConfirmation` (без LLM);
  2. прогресс `PARSING`;
  3. `eventParser.parse` → `LlmRequestException` → сообщение `LLM_FAILED`, выход;
  4. если `intent != CREATE` → `modifySchedule.handle(message, parsed)`, выход;
  5. прогресс `VALIDATING`; `validator.validate` → `ScheduleRejectionException` → `markProcessed` + сообщение-подсказка, выход;
  6. прогресс `SAVING`; `sendAfterPersist`.
- **Побочные эффекты:** публикация `ShowProgress`/`SendMessage` в канал.
- **Ошибки:** наружу не пробрасывает (обрабатывает внутри).

#### `sendAfterPersist(message, channel, validated)` → void (private)
- **Назначение:** транзакция сохранения + подтверждение после коммита.
- **Логика:** `transactions.executeWithoutResult`: проверка/фиксация `processed_events` (иначе `AlreadyProcessedException`);
  `userService.upsertByIdentity`; `saveSchedule`; `createReminder`; `commandJournal.save(confirmCommand)`;
  `afterCommit` → публикация `SendMessage` с `confirmation`.
- **Ошибки:** `AlreadyProcessedException`/`DataIntegrityViolationException` → повторная отправка подтверждения.

#### `saveSchedule(user, message, validated)` → userSchedules (private)
- **Назначение:** построить и сохранить запись (локальные date/time + абсолютные startAt/endAt + зона + источник).
- **Логика:** all-day → date из `remindAt`, time=null; иначе date/time из `startAt`.

#### `createReminder(schedule, validated)` → void (private)
- **Назначение:** создать напоминание (MIDNIGHT для all-day; T_MINUS_3H иначе).
- **Логика:** статус PENDING если `remindAt` в будущем, иначе SKIPPED.

#### `confirmCommand(commandId, schedule, message)` → CommandJournalEntry (private)
- **Назначение:** запись журнала с purpose `CONFIRM_SCHEDULE`, status PENDING.

#### `resendConfirmation(channel, schedule)` → void (private)
- **Назначение:** повтор подтверждения, только если последняя команда в PENDING.
- **Логика:** `findFirstByRelatedIdAndPurposeOrderByCreatedAtDesc`; тот же `commandId`.

#### `markProcessed(message)` → void (private)
- **Назначение:** зафиксировать событие (идемпотентно, глотает гонку `DataIntegrityViolationException`).

#### `processed(message)` → ProcessedEvent (private)
- **Назначение:** построить отметку с `CONSUMER_ID` и `processedAt`.

#### `providerOf(channel)` → AuthProvider (static, private)
- **Назначение:** TELEGRAM → `AuthProvider.TELEGRAM`; иначе `IllegalArgumentException`.

#### `newCommandId()` → String (static, private)
- **Назначение:** `UUID.randomUUID().toString()`.

#### `AlreadyProcessedException` (static final class, private)
- **Назначение:** сигнал «событие уже обработано другим потоком» внутри транзакции.

## ModifyScheduleUseCase (`@Service`)
Сценарий «перенести/удалить существующую запись». `MATCH_TIME_TOLERANCE = 30 мин`.

#### Конструкторы
- Публичный и package-private (с `Clock`/`ZoneId`/`Duration`), как у Create.
- **Ошибки:** `IllegalArgumentException` если `reminderOffset` не положителен.

#### `handle(IncomingMessage message, ParsedEvent parsed)` → void
- **Назначение:** обработать UPDATE/DELETE.
- **Логика:**
  1. дедуп по `processed_events` → выход;
  2. прогресс `VALIDATING`; для UPDATE — `validator.validate` (отказ → `markProcessed` + сообщение);
  3. прогресс `SAVING`; `upsertByIdentity`;
  4. `resolveMatchDate` → null → `markProcessed` + `CLARIFY_WHICH`;
  5. `matchCandidates` → size != 1 → `markProcessed` + `CLARIFY_NOT_FOUND`/`CLARIFY_SEVERAL`;
  6. ровно 1 → `sendAfterPersist`.
- **Побочные эффекты:** прогресс/сообщения в канал.

#### `resolveMatchDate(parsed)` → LocalDate (static, private)
- **Назначение:** дата из `matchDate` или из `startLocal`.

#### `matchCandidates(userId, date, parsed)` → List<userSchedules> (private)
- **Назначение:** ACTIVE-записи за день, суженные по времени и названию.

#### `timeMatches(schedule, matchTime)` → boolean (static, private)
- **Назначение:** без опорного времени — все; иначе |Δ| ≤ 30 мин (безвременные не подходят).

#### `titleMatches(scheduleTitle, matchTitle)` → boolean (static, private)
- **Назначение:** подстрока в любую сторону без регистра; пустая опора — не фильтровать.

#### `sendAfterPersist(message, channel, intent, validated, schedule)` → void (private)
- **Назначение:** транзакция: `processed_events`, UPDATE → `applyUpdate` + пересоздание напоминаний,
  DELETE → `CANCELED` + удаление напоминаний; `commandJournal.save`; подтверждение после коммита.
- **Ошибки:** `AlreadyProcessedException`/`DataIntegrityViolationException` → лог, выход.

#### `applyUpdate(schedule, validated)` → void (static, private)
- **Назначение:** обновить локальные поля, абсолютные моменты, зону-снимок.

#### `createReminder(schedule, validated)` → void (private)
- **Назначение:** новое напоминание (MIDNIGHT / T_MINUS_3H), статус PENDING/SKIPPED.

#### `clarify(message, channel, text, relatedId)` → void (private)
- **Назначение:** уточняющий вопрос: журнал `CLARIFY_SCHEDULE` + сообщение после коммита.

#### `modifyCommand(commandId, schedule, message, purpose)` → CommandJournalEntry (private)
- **Назначение:** запись журнала для update/delete.

#### `markProcessed(message)` / `processed(message)` → (private)
- **Назначение:** идемпотентная фиксация события (как в Create).

#### `providerOf(channel)` / `newCommandId()` → (static, private)
- **Назначение:** провайдер по каналу; новый UUID.

#### `AlreadyProcessedException` (static final class, private)
- **Назначение:** сигнал гонки внутри транзакции.

## ScheduleValidator (`@Service`)
Правила приёмки события. Константы: `TITLE_MAX_LENGTH=200`, `NOTE_MAX_LENGTH=1000`, тексты `HINT_*`.

#### Конструкторы
- Публичный `(defaultDurationMinutes, maxHorizonDays, maxDurationMinutes, pastToleranceSeconds)`.
- Package-private с `Duration pastTolerance`, `Clock`.
- **Ошибки:** `IllegalArgumentException` при неположительных лимитах/длительности.

#### `validate(ParsedEvent event, ZoneId userZone)` → ValidatedSchedule
- **Назначение:** валидировать событие.
- **Логика:** `!isEvent` → `HINT_NOT_AN_EVENT`; `ambiguous` → `HINT_AMBIGUOUS`; all-day → `validateAllDay`;
  нет `startLocal` → подсказка по `missing`; прошлое (с допуском) → `HINT_IN_THE_PAST`; за горизонтом → `HINT_TOO_FAR`;
  вычисление `end` (endLocal или start+duration); `duration<=0` → `HINT_END_BEFORE_START`; `>max` → `HINT_TOO_LONG`.
- **Возврат:** `ValidatedSchedule` (title/note обрезаны).
- **Ошибки:** `ScheduleRejectionException`.

#### `isAllDay(event)` → boolean (static, private)
- **Назначение:** `startLocal != null` и `missing` содержит `time`, но не `date`.

#### `validateAllDay(event, userZone)` → ValidatedSchedule (private)
- **Назначение:** проверка по дню (прошлое/горизонт), `remindAt` = 00:00 дня.

#### `resolveDuration(event)` → int (private)
- **Назначение:** длительность из события или `defaultDurationMinutes`.

#### `hintForMissing(missing)` → String (static, private)
- **Назначение:** выбрать подсказку по `missing`.

#### `truncate(value, maxLength)` → String (static, private)
- **Назначение:** обрезать/обнулить пустое.

#### `reject(hint)` → ScheduleRejectionException (static, private)

## ValidatedSchedule (record)
`(String title, String note, Instant startAt, Instant endAt, ZoneId zone, boolean allDay, Instant remindAt)`.
Доп. конструктор `(title, note, startAt, endAt, zone)` — allDay=false, remindAt=null (совместимость).

## handleSchedule (`@Service`)
Старый сценарий создания (через `LessonParser`), используется для простых записей.

#### Конструктор `handleSchedule(lessonParser, userSchedulesRepo, userService)`

#### `handleNewSchedule(String text, Long telegramId)` → void `@Transactional`
- **Назначение:** распарсить текст, создать запись.
- **Логика:** `lessonParser.parse`; нет даты/времени → `ScheduleException`; `upsertByTelegramId`;
  построить `userSchedules` (date/time/title/note) и сохранить.
- **Ошибки:** `ScheduleException` (date/time required), `LlmRequestException`.

#### `blankToNull(String)` → String (static, private)
- **Назначение:** пустое → null.

## ScheduleException (RuntimeException) / ScheduleRejectionException (extends ScheduleException)
`ScheduleException(String)`. `ScheduleRejectionException(userHint)` — отказ валидатора с подсказкой.

## parse/

### EventParser (interface)
#### `parse(String userText, ZoneId userZone)` → ParsedEvent
- **Назначение:** разобрать текст в событие (относительные даты — от зоны пользователя).

### Intent (enum)
`CREATE`, `UPDATE`, `DELETE`.
#### `from(String raw)` → Intent (static)
- **Назначение:** null/неизвестное → `CREATE`.

### ParsedEvent (record)
Поля: `isEvent`, `title`, `description`, `startLocal`, `endLocal`, `durationMinutes`, `missing:Set<String>`,
`ambiguous`, `intent`, `matchDate`, `matchTime`, `matchTitle`.
- Канонический конструктор: `missing` → immutable (null→`Set.of()`), `intent` null→CREATE, `matchTitle` trim/пусто→null.
- Доп. конструктор v1 (без intent/match).
#### `startAt(ZoneId)` / `endAt(ZoneId)` → Instant
- **Назначение:** абсолютный момент; `atZone` (DST).
#### `at(zone, value, field)` → Instant (static, private)
- **Назначение:** null → `IllegalStateException`; `value.atZone(zone).toInstant()`.

### LlmEventParser (`@Service implements EventParser`)
Основной парсер v2 через OpenRouter. Константы: `MAX_TOKENS=1024`, `ATTEMPT_TIMEOUT=12 c`, `TOTAL_BUDGET=25 c`.

#### Конструкторы
- Публичный `(client, mapper, model, fallbackModels)`.
- Package-private с `Clock`.
- `normalizeModels` — объединяет основную и запасные модели; пусто → `IllegalArgumentException`.

#### `parse(userText, userZone)` → ParsedEvent
- **Назначение:** разобрать текст, перебирая модели с бюджетом.
- **Логика:** пусто → `LlmRequestException`; дедлайн `now+25c`; для каждой модели, пока `attemptFits`:
  `client.post` → `toEvent`; ошибки копятся; все упали → `LlmRequestException`.
- **Ошибки:** `LlmRequestException`.

#### `attemptFits(deadline)` → boolean (private)
- **Назначение:** хватает ли остатка бюджета на целую попытку (≥12 c).

#### `request(model, userText, userZone)` → ChatRequest (private)
- **Назначение:** собрать запрос (system `EventPrompts.system`, `json_schema`, temperature 0, reasoning off, fallbacks).

#### `toEvent(response, model)` → ParsedEvent (private)
- **Назначение:** проверить choices/content → `map(parseObject(content))`.

#### `parseObject(content)` → JsonNode (private)
- **Назначение:** `readTree`, объект; иначе `LlmRequestException`.

#### `map(node)` → ParsedEvent (private)
- **Назначение:** собрать `ParsedEvent`; allDay добавляет `time` в `missing`.

#### `localDateOrNull` / `localTimeOrNull` (static, private)
- **Назначение:** безопасный разбор match-полей (мусор → null).

#### `localDateTimeOrNull(node, field)` → LocalDateTime (static, private)
- **Назначение:** строгий `yyyy-MM-dd'T'HH:mm`, затем ISO; иначе `LlmRequestException`.

#### `textOrNull` / `integerOrNull` / `missing` (static, private)
- **Назначение:** безопасные извлекатели; `missing` фильтрует только `date`/`time`.

#### `schema()` → JsonNode (private)
- **Назначение:** `EventPrompts.SCHEMA_JSON`; ошибка → `LlmRequestException`.