# 07 · javaService — обмен сообщениями (messaging)

Пакет `enerjy.musicxService.messaging` (+ `channel`, `contract`). Консьюмеры, процессоры, каналы, контракт.

## InboundMessageConsumer (`@Component`)
Консьюмер входящих сообщений. Ручной ack; ошибки десериализации/валидации → DLT сразу.

#### Конструктор `InboundMessageConsumer(objectMapper, createScheduleUseCase, timezoneCommands)`

#### `handle(String json, Acknowledgment ack)` → void `@KafkaListener(topics=inbound.messages, factory=ContractKafkaConfig.FACTORY)`
- **Назначение:** принять `IncomingMessage`, выбрать обработчик, подтвердить смещение.
- **Логика:** `readValue` → если `TimezoneCommandHandler.isTimezoneCommand` → `timezoneCommands.handle`,
  иначе `createScheduleUseCase.handle`; затем `ack.acknowledge()`.
- **Побочные эффекты:** коммит смещения после успеха.
- **Ошибки:** `JsonProcessingException` (в DLT, не ретраится).

## OutboundResultsConsumer (`@Component`)
Консьюмер результатов доставки. Обновляет журнал/напоминания/удаления.

#### Конструкторы
- Публичный `(objectMapper, commandJournal, deletions, reminders, transactions, deletionDelay, deletionRetryDelay, reminderMaxAttempts)`.
- Package-private с `Clock`.

#### `handle(String json, Acknowledgment ack)` → void `@KafkaListener(outbound.results)`
- **Назначение:** `readValue` → в транзакции `apply(result)` → ack.

#### `apply(DeliveryResult)` → void (private)
- **Назначение:** найти запись журнала по `commandId`, обновить статус, разветвить по purpose.

#### `applyStatus(result, entry)` → boolean (static, private)
- **Назначение:** SENT/ALREADY_GONE → SENT (+externalMessageId); BLOCKED/FAILED → FAILED; RATE_LIMITED/TRANSIENT → false.

#### `handleByPurpose(result, entry)` → void (private)
- **Назначение:** switch по purpose → `onConfirmationResult`/`onDeletionResult`/`onReminderResult`.

#### `onConfirmationResult(result, entry)` → void (private)
- **Назначение:** при SENT создать `ScheduledDeletion` (задержка `deletionDelay`); идемпотентно.

#### `onDeletionResult(result, entry)` → void (private)
- **Назначение:** SENT/ALREADY_GONE → DONE; RATE_LIMITED/TRANSIENT → PENDING + retry; BLOCKED/FAILED → FAILED.

#### `onReminderResult(result, entry)` → void (private)
- **Назначение:** SENT/ALREADY_GONE → SENT; BLOCKED → FAILED_PERMANENT; FAILED → FAILED;
  RATE_LIMITED/TRANSIENT → PENDING до `max-attempts`, затем FAILED.

#### `retryDelay(result)` → Duration (private)
- **Назначение:** `retryAfterSec` если >0, иначе `deletionRetryDelay`.

## ReminderProcessor (`@Component`)
Планировщик напоминаний (`@Scheduled` cron, default каждый час).

#### Конструкторы
- Публичный `(reminders, schedulesRepo, commandJournal, channels, transactions, stuckAfter, reminderOffset, midnightGrace, batchSize)`.
- Package-private с `Clock`. **Ошибки:** `IllegalArgumentException` при неположительных значениях.

#### `processDue()` → void `@Scheduled(cron="${app.reminders.cron:0 0 * * * *}")`
- **Назначение:** один тик в транзакции: `returnStuckToPending`, `expirePassed`, `lockDue` → `claim` каждого.

#### `claim(Reminder reminder, now)` → void (private)
- **Назначение:** захватить напоминание (SENDING, attempts+1), проверить расписание/канал, создать журнал,
  опубликовать `SendMessage` после коммита.
- **Логика:** нет расписания/канала → EXPIRED/FAILED; MIDNIGHT на timed-записи → FAILED; MIDNIGHT вне grace → EXPIRED;
  обычное без startAt → EXPIRED; неизвестный канал → FAILED.

#### `reminderCommand(commandId, schedule, reminder, now)` → CommandJournalEntry (private)
- **Назначение:** запись журнала purpose REMINDER.

## ScheduledDeletionProcessor (`@Component`)
Планировщик отложенных удалений (`@Scheduled` fixedDelay 5 c).

#### Конструкторы
- Публичный `(deletions, commandJournal, channels, transactions, stuckAfter, batchSize)`.
- Package-private с `Clock`. **Ошибки:** `IllegalArgumentException` при неположительных значениях.

#### `processDue()` → void `@Scheduled(fixedDelayString="${app.deletions.poll-interval-ms:5000}")`
- **Назначение:** тик: `returnStuckToPending`, `findByStatusAndDeleteAtLessThanEqual` → `claim`.

#### `claim(ScheduledDeletion task, now)` → void (private)
- **Назначение:** SENDING, attempts+1, выбрать канал (неизвестный → FAILED), журнал, `DeleteMessage` после коммита.

#### `deleteCommand(commandId, task, now)` → CommandJournalEntry (private)
- **Назначение:** запись журнала purpose DELETE_CONFIRMATION.

## TimezoneCommandHandler (`@Component`)
Команда `/timezone`. `COMMAND = "/timezone"`, `EXAMPLE = "Europe/Berlin"`.

#### `isTimezoneCommand(String text)` → boolean (static)
- **Назначение:** начинается ли текст с `/timezone` или `/timezone@bot`.

#### `handle(IncomingMessage message)` → void
- **Назначение:** показать текущую зону или сохранить новую (MANUAL).
- **Логика:** `upsertByIdentity`; без аргумента → текущая зона; `ZoneId.of` ошибка → подсказка; успех → `setTimezone(MANUAL)`.

#### `currentZoneMessage(user)` → String (static, private)
- **Назначение:** текст текущей зоны (кто задал).

#### `argument(String text)` → String (static, package-private)
- **Назначение:** аргумент после первого токена; пусто → null.

#### `publish(channel, conversationId, text)` → void (static, private)
- **Назначение:** `SendMessage.create` с новым UUID.

#### `providerOf(channel)` → AuthProvider (static, private)

## MessageTemplates (final)
Тексты пользователю (канал-агностичны). Константы: `PARSING`, `VALIDATING`, `SAVING`, `LLM_FAILED`,
`CLARIFY_NOT_FOUND`, `CLARIFY_SEVERAL`, `CLARIFY_WHICH`.

#### `confirmation/confirmUpdate/confirmDelete(userSchedules)` → String (static)
- **Назначение:** подтверждения создания/переноса/удаления (через `eventText`).

#### `eventText(headline, schedule)` → String (static, private)
- **Назначение:** каркас «заголовок + когда + описание»; all-day → дата + «весь день».

#### `reminder(schedule, reminder, offset)` → String (static)
- **Назначение:** текст напоминания (MIDNIGHT → `reminderAllDay`; иначе «через N — …»).

#### `reminderAllDay(schedule)` → String (static, private)
- **Назначение:** «Напоминание: сегодня — … весь день».

#### `duration(long minutes)` → String (static, package-private)
- **Назначение:** «N ч M мин».

## KafkaMessagePublisher (`@Component`)
Публикует сообщения контракта в нужный топик, не теряя ошибки.

#### Конструктор `KafkaMessagePublisher(kafkaTemplate, objectMapper)`

#### `publish(KafkaMessage message)` → `CompletableFuture<SendResult>`
- **Назначение:** выбрать топик через `Topics.forMessage` и отправить.

#### `publish(String topic, KafkaMessage message)` → `CompletableFuture<SendResult>`
- **Назначение:** сериализовать (ошибка → `IllegalStateException`), отправить с ключом `partitionKey`,
  логировать результат/ошибку через `whenComplete`.

## channel/

### OutboundChannel (interface)
- `id()` → String; `publish(OutboundCommand)` → void.

### KafkaOutboundChannel (`@Component implements OutboundChannel`)
- `id()` → `Channels.TELEGRAM`.
- `publish(command)` → проверка канала, `publisher.publish(command)`.

### ChannelRegistry (`@Component`)
#### Конструктор `ChannelRegistry(List<OutboundChannel>)`
- **Назначение:** собрать каналы в map; дубль id → `IllegalStateException`.
#### `by(String channelId)` → OutboundChannel
- **Назначение:** получить канал; неизвестный → `IllegalArgumentException`.

## contract/ (копии в обоих сервисах, синхронны)

### KafkaMessage (sealed interface)
Разрешены `IncomingMessage`, `OutboundCommand`, `DeliveryResult`. `CURRENT_SCHEMA_VERSION=1`.
Методы: `eventId()`, `schemaVersion()`, `occurredAt()`, `channel()`, `conversationId()`.
`default partitionKey()` = `channel + ":" + conversationId` (`@JsonIgnore`).
Jackson: `@JsonTypeInfo(property="type")`, `@JsonSubTypes(...)`, `NON_NULL`, `ignoreUnknown`.

### OutboundCommand (sealed interface extends KafkaMessage)
Разрешены `ShowProgress`, `SendMessage`, `DeleteMessage`.

### Envelope (final, package-private)
- `newEventId()` → UUID; `now()` → ISO-8601; `check(...)` (eventId/occurredAt/channel/conversationId непусты,
  schemaVersion ≥ 1); `requireText(value, field)`.

### IncomingMessage (record implements KafkaMessage)
Поля: конверт + `messageId`, `userRef`, `text`, `locale`, `progressRef`. `TYPE="INCOMING_MESSAGE"`.
Конструктор валидирует; `create(channel, conversationId, messageId, userRef, text, locale, progressRef)`.

### SendMessage (record implements OutboundCommand)
Конверт + `commandId`, `text`. `TYPE="SEND_MESSAGE"`. `create(...)`.

### DeleteMessage (record implements OutboundCommand)
Конверт + `commandId`, `externalMessageId`. `TYPE="DELETE_MESSAGE"`. `create(...)`.

### ShowProgress (record implements OutboundCommand)
Конверт + `draftRef`, `text`. `TYPE="SHOW_PROGRESS"`. `create(...)`.

### DeliveryResult (record implements KafkaMessage)
Конверт + `commandId`, `status:DeliveryStatus`, `externalMessageId`, `retryAfterSec:Integer`, `error`.
`TYPE="DELIVERY_RESULT"`. `create(...)`.

### DeliveryStatus (enum)
`SENT`, `ALREADY_GONE`, `BLOCKED`, `RATE_LIMITED`, `TRANSIENT_FAILURE`, `FAILED`.

### Topics (final)
Константы: `INBOUND_MESSAGES="inbound.messages"`, `OUTBOUND_RESULTS="outbound.results"`,
`OUTBOUND_COMMANDS_TELEGRAM="outbound.commands.telegram"`, `DLT_SUFFIX=".dlt"`.
- `outboundCommands(channel)` → `outbound.commands.<channel>`.
- `dlt(topic)` → `<topic>.dlt`.
- `forMessage(KafkaMessage)` → топик по типу.
- `contractTopics()` → все рабочие топики + DLT.

### Channels (final)
`TELEGRAM = "TELEGRAM"`.