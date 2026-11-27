# 03 · javaService — домен пользователя (user)

Пакет `enerjy.musicxService.user`. Сущности, репозитории, enum-ы, `userService`.

## Сущности

### userEntity (`@Entity @Table("user_entity")`, `@Data`)
«Кто это», без привязки к способу входа. Поля:
- `userId:Long` (`@Id @GeneratedValue(IDENTITY)`) — внутренний ключ.
- `name:String(255)` — отображаемое имя.
- `nickname:String(255)` — username у провайдера.
- `timezone:String(64)` — IANA-зона.
- `timezoneStatus:TimezoneStatus` (`@Enumerated(STRING)`, length 16).
Конструктор: `userEntity()` (пустой, для JPA).

### userIdentityEntity (`@Entity @Table("user_identity")`, `@Data`)
Связка «провайдер → пользователь». Уникальность `(provider, provider_user_id)`.
Поля: `identityId:Long` (PK), `userId:Long` (не null), `provider:AuthProvider` (STRING, 32),
`providerUserId:String(255)` (строка — у разных провайдеров разный формат).

### userSchedules (`@Entity @Data`)
Запись расписания. Поля:
- `sceduleId:Long` (PK, историческая опечатка — не переименовывать).
- `userId:Long` (не null).
- `date:LocalDate` (не null) — локальная дата, источник для `/calendar`.
- `time:LocalTime` (nullable; null = «весь день», V11).
- `title:String(200)`, `note:String(1000)`.
- `startAt:Instant` (`@JdbcTypeCode(TIMESTAMP_UTC)`), `endAt:Instant` — абсолютные моменты.
- `timezone:String(64)` — снимок зоны.
- `status:ScheduleStatus` (STRING, 16, default `ACTIVE`).
- `sourceChannel:String(32)`, `sourceConversationId:String(255)`, `sourceMessageId:String(64)` — тройка дедупа.
- `createdAt:Instant`.

### Reminder (`@Entity @Table("reminders")`, `@Data`)
Напоминание. Уникальность `(schedule_id, kind)`. Поля: `id:Long` (PK), `scheduleId:Long`,
`kind:ReminderKind` (STRING 32), `remindAt:Instant` (не null), `status:ReminderStatus` (STRING 16),
`attempts:int` (не null), `claimedAt:Instant`, `sentAt:Instant`.

### ScheduledDeletion (`@Entity @Table("scheduled_deletions")`, `@Data`)
Задача отложенного удаления. Уникальность `(channel, conversation_id, external_message_id)`.
Поля: `id:Long` (PK), `channel:String(32)`, `conversationId:String(255)`, `externalMessageId:String(64)`,
`deleteAt:Instant` (не null), `status:DeletionStatus` (STRING 16), `attempts:int`,
`claimedAt:Instant`, `commandId:String(64)` (связь с последней командой удаления), `createdAt:Instant` (не null).

### CommandJournalEntry (`@Entity @Table("outbound_commands")`, `@Data`)
Журнал команд ядра транспорту. PK — `commandId:String(64)` (не `@GeneratedValue`).
Поля: `type:String(32)`, `channel:String(32)`, `conversationId:String(255)`,
`purpose:CommandPurpose` (STRING 32), `relatedId:Long` (scheduleId/reminderId), `status:CommandDeliveryStatus` (STRING 16),
`externalMessageId:String(64)`, `createdAt:Instant` (не null).

### ProcessedEvent (`@Entity @Table("processed_events")`, `@IdClass(ProcessedEventId.class)`, `@Data`)
Отметка «событие обработано консьюмером» (идемпотентность). Составной ключ:
`eventId:String(64)` + `consumer:String(64)`; поле `processedAt:Instant` (не null).

### ProcessedEventId (`@Data @NoArgsConstructor @AllArgsConstructor`, `Serializable`)
Составной ключ `processed_events`: `eventId`, `consumer`.

## Enum-ы

- **ScheduleStatus**: `ACTIVE`, `CANCELED`.
- **ReminderKind**: `T_MINUS_3H` (за N часов, сейчас 3), `MIDNIGHT` (00:00 дня «весь день»).
- **ReminderStatus**: `PENDING`, `SENDING`, `SENT`, `SKIPPED`, `FAILED`, `FAILED_PERMANENT`, `EXPIRED`.
- **DeletionStatus**: `PENDING`, `SENDING`, `DONE`, `FAILED`.
- **TimezoneStatus**: `AUTO` (детект Mini App), `MANUAL` (команда /timezone; важнее).
- **CommandPurpose**: `CONFIRM_SCHEDULE`, `CONFIRM_UPDATE`, `CONFIRM_DELETE`, `CLARIFY_SCHEDULE`,
  `DELETE_CONFIRMATION`, `REMINDER`.
- **CommandDeliveryStatus**: `PENDING`, `SENT`, `FAILED`.

## Репозитории (Spring Data)

### userRepo
`extends JpaRepository<userEntity, Long>`.
- `findByUserId(Long)` → `Optional<userEntity>`.
- `existsByUserId(Long)` → boolean.

### userIdentityRepo
`extends JpaRepository<userIdentityEntity, Long>`.
- `findByProviderAndProviderUserId(AuthProvider, String)` → `Optional`.
- `findByUserId(Long)` → `List`.
- `existsByProviderAndProviderUserId(AuthProvider, String)` → boolean.

### userSchedulesRepo
`extends JpaRepository<userSchedules, Long>`.
- `findByUserIdAndDateBetweenOrderByTimeAsc(userId, from, to)` → `List`.
- `findByUserIdAndStatusAndDateBetweenOrderByTimeAsc(userId, status, from, to)` → `List` (только ACTIVE для календаря).
- `findByUserIdAndStatusAndDate(userId, status, date)` → `List` (кандидаты матчинга).
- `findBySourceChannelAndSourceConversationIdAndSourceMessageId(channel, conv, msg)` → `Optional` (дедуп).
- `countByUserId(Long)` → long.

### ScheduledDeletionRepo
`extends JpaRepository<ScheduledDeletion, Long>`.
- `existsByChannelAndConversationIdAndExternalMessageId(...)` → boolean.
- `findByStatusAndDeleteAtLessThanEqual(status, deleteAt, Pageable)` → `List` — `@Lock(PESSIMISTIC_WRITE)` +
  `@QueryHints(lock.timeout=-2)` (SKIP LOCKED) для параллельных тиков.
- `returnStuckToPending(to, from, deadline)` → int — `@Modifying(clearAutomatically=true)`, возвращает зависшие SENDING→PENDING.

### ReminderRepo
`extends JpaRepository<Reminder, Long>`.
- `findByScheduleId(Long)` → `List`.
- `deleteByScheduleId(Long)` → void — bulk JPQL `@Modifying(clearAutomatically=true)` (срабатывает сразу).
- `lockDue(status, now, midnightKind, cutoff, Pageable)` → `List` — `@Lock(PESSIMISTIC_WRITE)` + SKIP LOCKED;
  выбирает due-напоминания, включая ветку MIDNIGHT для безвременных записей.
- `returnStuckToPending(to, from, deadline)` → int.
- `expirePassed(to, from, now, midnightKind, cutoff)` → int — гасит PENDING, чьё событие уже началось
  (или MIDNIGHT с закрытым grace-окном).

### CommandJournalRepo
`extends JpaRepository<CommandJournalEntry, String>`.
- `findFirstByRelatedIdAndPurposeOrderByCreatedAtDesc(relatedId, purpose)` → `Optional` (последняя команда по цели).

### ProcessedEventRepo
`extends JpaRepository<ProcessedEvent, ProcessedEventId>` — без методов.

## userService
Работа с пользователями и способами входа.

#### Конструктор `userService(userRepository, identityRepository, schedulesRepo)`
- **Побочные эффекты:** нет.

#### `upsertByIdentity(provider, providerUserId, name, nickname)` → `userEntity` `@Transactional`
- **Назначение:** найти по (провайдер, id) или создать пользователя + связку.
- **Параметры:** `provider`, `providerUserId` (строка), `name`, `nickname` (nullable).
- **Логика:** `providerUserId` пусто → `IllegalArgumentException`; если identity есть — обновить `name`/`nickname`
  (если не null) и сохранить; иначе создать `userEntity` + `userIdentityEntity`.
- **Возврат:** сохранённый `userEntity`.
- **Ошибки:** `IllegalArgumentException`; `IllegalStateException` если identity указывает на отсутствующего пользователя.

#### `findById(long userId)` → `Optional<userEntity>`
- **Назначение:** найти по внутреннему id.

#### `upsertByTelegramId(long telegramId)` → `userEntity` `@Transactional`
- **Назначение:** обёртка над `upsertByIdentity(TELEGRAM, String.valueOf(id), null, null)`.

#### `findAll()` → `List<userEntity>`
- **Назначение:** все пользователи (для отладки/профиля).

#### `setTimezone(long userId, ZoneId zone, TimezoneStatus status)` → `userEntity` `@Transactional`
- **Назначение:** сохранить зону; `AUTO` не перетирает `MANUAL`.
- **Логика:** если `status==AUTO` и текущий `MANUAL` — вернуть без изменений; иначе записать зону+статус.
- **Ошибки:** `IllegalArgumentException` если пользователь не найден.

#### `findIdentities(long userId)` → `List<userIdentityEntity>`
- **Назначение:** все способы входа пользователя.

#### `countSchedules(long userId)` → long
- **Назначение:** число записей пользователя.