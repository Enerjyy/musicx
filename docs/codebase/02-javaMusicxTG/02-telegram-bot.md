# 02 · javaMusicxTG — Telegram-бот и Bot API

Пакеты `TelegramBot.BotInit`, `TelegramBot.TGAPI.*`.

## MusicxBot (extends TelegramLongPollingBot, `@Component`)
Приём сообщений. Бот — только транспорт.

- `PRIVATE_CHAT = "private"` — тип личного чата.

#### Конструктор `MusicxBot(botUsername, botToken, publisher, gateway)`
- **Параметры:** `bot.name`, `bot.token`, `KafkaMessagePublisher`, `TelegramGateway`.

#### `getBotUsername()` → String
- **Назначение:** username бота (для Telegram API).

#### `getBotToken()` → String
- **Назначение:** токен бота.

#### `onUpdateReceived(Update update)` → void
- **Назначение:** принять личное текстовое сообщение, создать черновик, опубликовать `IncomingMessage`.
- **Логика:** игнорировать null/без сообщения/без текста/не-личный чат; `chatId`, случайный `draftId`;
  `gateway.sendMessageDraft(chatId, draftId, "")` (пустой текст → «Thinking…», ошибка → лог);
  `publisher.publish(IncomingMessage.create(TELEGRAM, chatId, messageId, fromId, text, languageCode, draftId))`.
- **Побочные эффекты:** вызов Telegram API + публикация в Kafka.
- **Ошибки:** `TelegramApiException` при черновике — только лог (не пробрасывается).

## TGAPI/Sending/SendAPI (`@Service implements TelegramGateway`)
Реализация шлюза поверх HTTP Bot API.

#### Конструктор `SendAPI(webAPIInstance, restTemplate, objectMapper)`

#### `sendMessageDraft(chatId, draftId, text)` → void
- **Назначение:** `POST sendMessageDraft` (chat_id, draft_id, text).

#### `sendMessage(chatId, text)` → long
- **Назначение:** `POST sendMessage`, вернуть `message_id`.
- **Возврат:** id отправленного сообщения.
- **Ошибки:** `TelegramApiException` (нет message_id → FAILED).

#### `deleteMessage(chatId, messageId)` → void
- **Назначение:** `POST deleteMessage`.

#### `post(method, payload)` → String (private)
- **Назначение:** выполнить POST к `apiUrl+method`.
- **Ошибки:** `HttpStatusCodeException` → `mapFailure`; `RestClientException` → TRANSIENT_FAILURE; `requireOk`.

#### `requireOk(method, body)` → void (private)
- **Назначение:** проверить `ok`; иначе `mapFailure`.

#### `mapFailure(method, statusCode, body)` → TelegramApiException (private)
- **Назначение:** разобрать тело и смапить код.

#### `mapFailure(method, code, description, response)` → TelegramApiException (private)
- **Логика:** 403 → BLOCKED; 429 → RATE_LIMITED (+retry_after); 400 «message to delete not found» → ALREADY_GONE;
  ≥500 → TRANSIENT_FAILURE; иначе FAILED.

#### `retryAfter(response)` → Integer (static, private)
- **Назначение:** `parameters.retry_after`.

#### `extractMessageId(body)` → long (private)
- **Назначение:** `result.message_id`; нет → FAILED.

#### `parse(method, body)` → TelegramResponse (private)
- **Назначение:** десериализовать; ошибка → TRANSIENT_FAILURE.

- Вложенные record: `TelegramResponse(ok, result, error_code, description, parameters)`,
  `Result(message_id)`, `Parameters(retry_after)` — все `@JsonIgnoreProperties(ignoreUnknown=true)`.

## TGAPI/System/ApiConfig (`@Configuration`)
#### `restTemplate(connectTimeoutSeconds, readTimeoutSeconds)` → `RestTemplate` (`@Bean`)
- **Назначение:** `RestTemplate` с таймаутами (connect 5 c, read 12 c) — иначе зависший вызов держит поток консьюмера.

## TGAPI/System/WebAPIInstance (`@Component`)
#### Конструктор `WebAPIInstance(restTemplate, botToken)`
- **Назначение:** собрать базовый URL `https://api.telegram.org/bot<token>/`.
#### `getApiUrl()` → String
#### `getRestTemplate()` → RestTemplate