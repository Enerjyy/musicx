# 05 · javaMusicxTG — обмен сообщениями (messaging)

Пакет `enerjy.muscxTG.messaging` (+ `contract`). Публикатор и контракт (зеркало ядра).

## KafkaMessagePublisher (`@Component`)
Публикует сообщения контракта в нужный топик, не теряя ошибки отправки.

#### Конструктор `KafkaMessagePublisher(kafkaTemplate, objectMapper)`

#### `publish(KafkaMessage message)` → `CompletableFuture<SendResult<String,String>>`
- **Назначение:** выбрать топик через `Topics.forMessage` и отправить.

#### `publish(String topic, KafkaMessage message)` → `CompletableFuture<SendResult<String,String>>`
- **Назначение:** сериализовать (ошибка → `IllegalStateException`), отправить с ключом `partitionKey`,
  логировать результат/ошибку через `whenComplete`.

## contract/ (зеркало `javaService` контракта)

Файлы идентичны ядру по структуре (отличия — только имя пакета `enerjy.muscxTG` и кодировка части
комментариев). Изменения вносить синхронно; совпадение фикстур проверяют контрактные тесты.

### KafkaMessage (sealed interface)
Разрешены `IncomingMessage`, `OutboundCommand`, `DeliveryResult`; `CURRENT_SCHEMA_VERSION=1`.
Методы конверта + `default partitionKey()` (`channel:conversationId`). Jackson: `@JsonTypeInfo(property="type")`,
`@JsonSubTypes`, `NON_NULL`, `ignoreUnknown`.

### OutboundCommand (sealed interface extends KafkaMessage)
Разрешены `ShowProgress`, `SendMessage`, `DeleteMessage`.

### Envelope (final, package-private)
`newEventId()`, `now()`, `check(...)`, `requireText(...)`.

### IncomingMessage (record)
Конверт + `messageId`, `userRef`, `text`, `locale`, `progressRef`; `TYPE="INCOMING_MESSAGE"`; `create(...)`.

### SendMessage (record)
Конверт + `commandId`, `text`; `TYPE="SEND_MESSAGE"`; `create(...)`.

### DeleteMessage (record)
Конверт + `commandId`, `externalMessageId`; `TYPE="DELETE_MESSAGE"`; `create(...)`.

### ShowProgress (record)
Конверт + `draftRef`, `text`; `TYPE="SHOW_PROGRESS"`; `create(...)`; `DeliveryResult` не требуется.

### DeliveryResult (record)
Конверт + `commandId`, `status`, `externalMessageId`, `retryAfterSec`, `error`; `TYPE="DELIVERY_RESULT"`; `create(...)`.

### DeliveryStatus (enum)
`SENT`, `ALREADY_GONE`, `BLOCKED`, `RATE_LIMITED`, `TRANSIENT_FAILURE`, `FAILED`.

### Topics (final)
`INBOUND_MESSAGES`, `OUTBOUND_RESULTS`, `OUTBOUND_COMMANDS_TELEGRAM`, `DLT_SUFFIX`.
- `outboundCommands(channel)`, `dlt(topic)`, `forMessage(KafkaMessage)`, `contractTopics()`.

### Channels (final)
`TELEGRAM = "TELEGRAM"`.