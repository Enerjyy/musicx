# 02 · javaService — безопасность (security)

Пакет `enerjy.musicxService.security` + `user.UserPrincipal`.

## AuthProvider (enum)
Способ входа. Значения: `TELEGRAM`. Пишется в БД строкой (`@Enumerated(EnumType.STRING)`),
поэтому новый провайдер не требует миграции. Используется в `userIdentityEntity`, `JwtService`, `AuthenticatedUser`.

## AuthenticatedUser (record)
`record AuthenticatedUser(long userId, AuthProvider provider)` — результат проверки токена.
`JwtAuthFilter` кладёт `UserPrincipal` с теми же данными в request attribute.

## TelegramUser (record)
Пользователь из подписанных `initData`. Поля: `id:long`, `firstName`, `lastName`, `username`,
`languageCode`, `isPremium:boolean`. Все поля помечены `@JsonProperty` (snake_case ↔ camelCase),
`@JsonIgnoreProperties(ignoreUnknown=true)`. Без аннотаций Jackson оставил бы строки null.

## InitDataException (RuntimeException)
Ошибка проверки initData (пусто, дубли, плохая подпись, просрочено, битый user-json).
Конструкторы: `(String)`, `(String, Throwable)`.

## TokenException (RuntimeException)
Токен не прошёл проверку (подпись/срок/формат). Конструкторы: `(String)`, `(String, Throwable)`.

## JwtService
Выдача и проверка собственных JWT (HS256).

#### Конструктор `JwtService(secret, ttlSeconds, issuer)`
- **Назначение:** инициализирует ключ подписи.
- **Параметры:** `jwt.secret`, `jwt.ttl-seconds`, `jwt.issuer`.
- **Ошибки:** `IllegalStateException`, если секрет < 32 байт (HS256 требует 256 бит) — падение на старте.
- **Побочные эффекты:** создаёт `SecretKey` через `Keys.hmacShaKeyFor`.

#### `issue(long userId, AuthProvider provider)` → `String`
- **Назначение:** выдать JWT.
- **Параметры:** `userId` (в subject), `provider` (claim `provider`).
- **Возврат:** компактный JWT с `iss`, `sub`, `iat`, `exp` (`now + ttl`), подписью.
- **Побочные эффекты:** нет.

#### `parse(String token)` → `AuthenticatedUser`
- **Назначение:** проверить подпись/срок/issuer и извлечь userId+provider.
- **Возврат:** `AuthenticatedUser(userId, provider)`.
- **Ошибки:** `TokenException` при любой ошибке парсинга (`ExpiredJwt`, `Signature`, `Malformed`),
  отсутствии subject, нечисловом subject.
- **Логика provider:** `parseProvider` — null/пустой/неизвестный → `TELEGRAM` (обратная совместимость).

#### `parseProvider(String raw)` → `AuthProvider` (static, private)
- **Назначение:** разобрать claim `provider`; неизвестное значение → `TELEGRAM`.
- **Возврат:** `AuthProvider`.

#### `getTtlSeconds()` → `long`
- **Назначение:** вернуть TTL (нужно фронту для планирования обновления).

## JwtAuthFilter (extends OncePerRequestFilter)
Извлекает пользователя из `Authorization: Bearer <jwt>`.

- `PRINCIPAL_ATTRIBUTE = "principal"` — ключ request attribute.
- `PUBLIC_PATHS = {"/auth/telegram"}` — без токена.

#### `shouldNotFilter(request)` → boolean
- **Назначение:** пропускать фильтр для OPTIONS (CORS-preflight), `/error`, `/auth/telegram`.
- **Возврат:** true, если фильтр не нужен.

#### `doFilterInternal(request, response, chain)` → void
- **Назначение:** проверить заголовок, распарсить токен, положить `UserPrincipal`.
- **Логика:** нет/не `Bearer ` → `reject`; пустой токен → `reject`; `jwtService.parse` → `TokenException` → `reject`;
  успех → `request.setAttribute("principal", new UserPrincipal(userId, provider))` и `chain.doFilter`.
- **Побочные эффекты:** пишет 401 JSON при отказе.

#### `reject(response)` → void (private)
- **Назначение:** 401 с телом `{"error":"unauthorized"}`, `Content-Type: application/json;charset=UTF-8`.
- **Примечание:** не логирует токен и не раскрывает причину.

## TGDataValidation
Проверка подписи Telegram Mini App `initData` (HMAC-SHA256 + `auth_date`).

#### Конструктор `TGDataValidation(botToken, objectMapper)`
- **Параметры:** `telegram.bot-token`, общий `ObjectMapper`.
- **Побочные эффекты:** нет.

#### `validate(String initData, long maxAgeSeconds)` → `TelegramUser`
- **Назначение:** проверить подпись и свежесть, вернуть пользователя.
- **Параметры:** `initData` (строка из `Telegram.WebApp.initData`), `maxAgeSeconds`.
- **Алгоритм:** split `&` → `TreeMap` (алфавитный порядок) с url-decode; дубли `hash`/ключей → отказ;
  `data-check-string` = `key=value` через `\n`; `secret = HMAC("WebAppData", botToken)`;
  `expected = hex(HMAC(secret, dataCheckString))`; constant-time сравнение; проверка `auth_date`
  (не в будущем, не старше `maxAgeSeconds`); `parseUser`.
- **Возврат:** `TelegramUser`.
- **Ошибки:** `InitDataException` (empty, duplicate hash/parameter, missing hash, no data, bad signature,
  auth_date missing/bad/future/expired, missing/bad user); `IllegalStateException` если `bot-token` не задан.
- **Побочные эффекты:** нет.

#### `urlDecode(String)` → String (static, private)
- **Назначение:** percent-decode, `+` → пробел (`URLDecoder`, UTF-8).

#### `parseAuthDate(String)` → long (static, private)
- **Назначение:** разобрать `auth_date`; пусто/нечисло → `InitDataException`.

#### `hmacHex(String dataCheckString)` → String (private)
- **Назначение:** двухступенчатый HMAC (шаги 3–4), hex.
- **Ошибки:** `InitDataException("hmac failure")`.

#### `constantTimeEquals(String a, String b)` → boolean (static, private)
- **Назначение:** сравнение за постоянное время (`MessageDigest.isEqual`).

#### `parseUser(String json)` → TelegramUser (private)
- **Назначение:** десериализовать `user`; `id <= 0` → отказ.
- **Ошибки:** `InitDataException` (missing user, bad user json, no valid id).

## UserPrincipal (record, пакет user)
`record UserPrincipal(long userId, AuthProvider provider)` — «кто пришёл», кладётся фильтром,
достаётся контроллерами через `@RequestAttribute("principal")`.