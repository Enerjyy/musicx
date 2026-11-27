# 03 · javaMusicxTG — команды Telegram

Пакет `telegram.commands`. `TelegramCommandListener`, `LruCommandCache`.

## TelegramCommandListener (`@Component`)
Исполняет команды ядра и отвечает `DeliveryResult`. `ShowProgress` не даёт результата; устаревший
(> `progress-ttl`, ~30 c) отбрасывается; `SendMessage`/`DeleteMessage` дедуплицируются LRU по `commandId`.

#### Конструктор `TelegramCommandListener(objectMapper, gateway, publisher, progressTtl, dedupCapacity)`
- **Параметры:** `app.telegram.progress-ttl` (PT30S), `app.telegram.dedup-capacity` (10000).
- **Побочные эффекты:** создаёт `LruCommandCache`.

#### `handle(String json, Acknowledgment ack)` → void `@KafkaListener(outbound.commands.telegram, factory=ContractKafkaConfig.FACTORY)`
- **Назначение:** `readValue` → `handle(command)` → ack.

#### `handle(OutboundCommand command)` → void (package-private)
- **Назначение:** разветвить по типу: `ShowProgress`→`showProgress`, `SendMessage`→`send`, `DeleteMessage`→`delete`.

#### `showProgress(ShowProgress progress)` → void (private)
- **Назначение:** обновить черновик статуса.
- **Логика:** stale → лог, выход; `gateway.sendMessageDraft(chatId, draftRef, text)`;
  `TelegramApiException` → лог (без ретрая); `NumberFormatException` → лог.

#### `send(SendMessage command)` → void (private)
- **Назначение:** отправить сообщение и ответить `DeliveryResult`.
- **Логика:** `processedCommands.add` (false → дубль, выход); `gateway.sendMessage` → `DeliveryResult(SENT, messageId)`;
  `TelegramApiException` → `publishFailure`.

#### `delete(DeleteMessage command)` → void (private)
- **Назначение:** удалить сообщение и ответить `DeliveryResult`.
- **Логика:** дедуп; `gateway.deleteMessage`; успех → SENT; ошибка → ALREADY_GONE c externalMessageId или статус из исключения.

#### `publishFailure(SendMessage command, TelegramApiException e)` → void (private)
- **Назначение:** RATE_LIMITED/TRANSIENT → освободить id (для редоставки) и опубликовать `DeliveryResult` с ошибкой.

#### `isStale(String occurredAt)` → boolean (private)
- **Назначение:** `Duration(now - occurredAt) > progressTtl`; битая дата → true.

#### `chatId(OutboundCommand command)` → long (static, private)
- **Назначение:** `Long.parseLong(conversationId)`.

## LruCommandCache (final, package-private)
Best-effort дедуп по `commandId` (LRU ограниченной ёмкости).

#### Конструктор `LruCommandCache(capacity)`
- **Параметры:** `capacity` (>0, иначе `IllegalArgumentException`).
- **Реализация:** `Collections.newSetFromMap(LinkedHashMap(removeEldestEntry))`.

#### `add(String commandId)` → boolean `synchronized`
- **Назначение:** true — новый; false — уже исполнялся (повторная доставка).

#### `remove(String commandId)` → void `synchronized`
- **Назначение:** освободить id после устранимого сбоя.

#### `capacity()` → int
- **Назначение:** вернуть ёмкость.