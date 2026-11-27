# 05 · javaService — LLM-слой (LlmRequests)

Пакет `enerjy.musicxService.LlmRequests` (+ `dto`, `prompts`). Клиент OpenRouter и парсеры.

## OpenRouterClient (`@Component`)
HTTP-клиент OpenRouter (`/chat/completions`).

#### Конструктор `OpenRouterClient(baseUrl, apiKey, readTimeoutSeconds)`
- **Параметры:** `openrouter.base-url`, `openrouter.api-key`, `openrouter.attempt-timeout-seconds` (12).
- **Настройка:** `SimpleClientHttpRequestFactory` (connectTimeout 5 c, readTimeout из параметра),
  `RestClient` с `Authorization: Bearer`, `Content-Type: application/json`.
- **Побочные эффекты:** нет.

#### `post(ChatRequest request)` → `ChatRequest.Response`
- **Назначение:** выполнить запрос к `/chat/completions`.
- **Возврат:** десериализованный `Response`.
- **Ошибки:** `RestClientResponseException` → `LlmRequestException` (тело обрезается до 500 символов);
  любое иное → `LlmRequestException` с типом и сообщением.

## LessonParser (`@Service`)
Парсер записей (старый контракт, используется `handleSchedule`).

- Константы: `MAX_TOKENS=1024`, списки `DATE_FORMATS` (13 форматов) и `TIME_FORMATS` (5 форматов).

#### Конструктор `LessonParser(client, mapper, model, timeZone)`
- **Параметры:** `openrouter.model`, `app.time-zone` (Clock в этой зоне).

#### `parse(String userText)` → `LessonParseResult`
- **Назначение:** разобрать текст в дату/время/название/заметку.
- **Логика:** пусто → `LlmRequestException`; `ChatRequest` с `LessonPrompts.system`, `json_schema`,
  `Provider(require_parameters=true)`; `client.post`; проверка choices/content; `toResult(parseObject(content))`.
- **Возврат:** `LessonParseResult`.
- **Ошибки:** `LlmRequestException` (empty input/response/content, bad json).

#### `schema()` → JsonNode (private)
- **Назначение:** распарсить `LessonPrompts.SCHEMA_JSON`; ошибка → `LlmRequestException`.

#### `parseObject(String)` → JsonNode (private)
- **Назначение:** `readTree`, проверить что объект; иначе `LlmRequestException`.

#### `toResult(JsonNode)` → LessonParseResult (static, private)
- **Назначение:** собрать `LessonParseResult(date, time, title, note)`.

#### `parseDate(String)` → LocalDate (static, package-private)
- **Назначение:** нормализовать и распарсить дату по `DATE_FORMATS`; не вышло → `LlmRequestException`.

#### `parseTime(String)` → LocalTime (static, package-private)
- **Назначение:** нормализовать и распарсить время по `TIME_FORMATS`; не вышло → `LlmRequestException`.

#### `unquote(String)` → String (static, private)
- **Назначение:** снять обрамляющие кавычки.

#### `normalizeDate(String)` → String (static, private)
- **Назначение:** убрать время, «года/г/year», обрезать по `T`.

#### `normalizeTime(String)` → List<String> (static, private)
- **Назначение:** убрать «мск/msk/utc±…», вернуть варианты (целиком, без хвоста, с `.`→`:`).

#### `text(JsonNode, field)` → String (static, private)
- **Назначение:** безопасно достать непустую строку (иначе null).

## dto/ChatRequest (record, `@JsonInclude(NON_NULL)`)
Запрос к OpenRouter. Поля: `model`, `messages:List<Message>`, `responseFormat` (`response_format`),
`maxTokens` (`max_tokens`), `provider:Provider`, `models:List<String>` (fallback), `temperature:Double`,
`reasoning:Reasoning`.
- Вложенные record: `Message(role, content)`, `ResponseFormat(type, jsonSchema)`, `JsonSchema(name, strict, schema)`,
  `Provider(requireParameters)`, `Reasoning(enabled)`, `Response(choices, usage)`, `Choice(message, finishReason)`,
  `Usage(promptTokens, completionTokens, totalTokens, cost)` (все с `@JsonIgnoreProperties(ignoreUnknown=true)`).
- Доп. конструктор `(model, messages, responseFormat, maxTokens, provider)` — совместимость (без fallback/temperature/reasoning).

## dto/LessonParseResult (record)
`(LocalDate date, LocalTime time, String title, String note)`.

## prompts/EventPrompts (final)
Промпт + JSON-схема контракта v2 (intent/match/allDay).

#### `system(ZonedDateTime userNow)` → String (static)
- **Назначение:** подставить текущие локальные дату/день недели/время/зону в шаблон.
- **Возврат:** системный промпт.

- `SCHEMA_JSON` (константа) — JSON Schema v2; `SCHEMA_NAME = "calendar_event"`.
- Приватный конструктор (только статика).

## prompts/LessonPrompts (final)
Промпт + схема старого контракта.

#### `system(LocalDate today)` → String (static)
- **Назначение:** подставить сегодняшнюю дату и день недели.
- **Возврат:** системный промпт.

- `SCHEMA_JSON` (константа), `SCHEMA_NAME = "lesson"`.

## LlmRequestException (RuntimeException)
Ошибка LLM-запроса/разбора. Конструкторы `(String)`, `(String, Throwable)`.