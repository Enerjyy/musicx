# 04 · javaMusicxTG — шлюз Telegram

Пакет `telegram.gateway`.

## TelegramGateway (interface)
Транспортный шлюз к Telegram Bot API. Ошибки — `TelegramApiException` со статусом `DeliveryStatus`.

#### `sendMessageDraft(long chatId, int draftId, String text)` → void
- **Назначение:** стриминг черновика статуса (пустой текст → «Thinking…»); `DeliveryResult` не требуется.

#### `sendMessage(long chatId, String text)` → long
- **Назначение:** отправить сообщение; вернуть `message_id`.

#### `deleteMessage(long chatId, long messageId)` → void
- **Назначение:** удалить ранее отправленное сообщение.

## TelegramApiException (RuntimeException)
Ошибка вызова Bot API со статусом контракта.

- Поля: `status:DeliveryStatus`, `retryAfterSec:Integer`.
#### Конструкторы
- `(DeliveryStatus status, Integer retryAfterSec, String message)`.
- `(DeliveryStatus status, Integer retryAfterSec, String message, Throwable cause)`.
#### `status()` → DeliveryStatus
#### `retryAfterSec()` → Integer