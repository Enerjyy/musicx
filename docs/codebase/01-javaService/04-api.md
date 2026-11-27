# 04 · javaService — REST API

Пакеты `enerjy.musicxService.api` и `api.applicationAPIs`, `api.dto`.

## AuthController (`@RestController @RequestMapping("/auth")`)
Обмен подтверждённых данных провайдера на JWT.

#### Конструктор `AuthController(validation, jwtService, userService, maxAgeSeconds)`
- **Параметры:** `TGDataValidation`, `JwtService`, `userService`, `telegram.max-age-seconds`.

#### `login(@Valid @RequestBody AuthRequest request)` → `ResponseEntity<AuthResponse>` `POST /auth/telegram`
- **Назначение:** проверить initData, найти/создать пользователя, выдать JWT.
- **Логика:** `validation.validate` → `upsertByIdentity(TELEGRAM, id, firstName, username)` →
  `jwtService.issue(userId, TELEGRAM)` → `AuthResponse(token, ttl, UserDto.from(user))`.
- **Возврат:** 200 + `AuthResponse`.
- **Ошибки:** `InitDataException` → 401 (через `GlobalExceptionHandler`); `MethodArgumentNotValid` → 400.

#### `debugMe(@RequestAttribute UserPrincipal principal)` → `ResponseEntity<Map<String,Object>>` `GET /auth/debug/me`
- **Назначение:** отладочный вывод (userId, provider, user, identities, scheduleCount).
- **Возврат:** 200 + карта.

## CalendarController (`@RestController @RequestMapping("/calendar")`)
Календарь пользователя (userId из токена).

#### Конструктор `CalendarController(userService, schedulesRepo)`

#### `getCalendar(year, month, principal)` → `ResponseEntity<List<CalendarEntryDto>>` `GET /calendar?year=&month=`
- **Назначение:** записи месяца только в статусе ACTIVE.
- **Параметры:** `year`, `month` (1–12), `principal` (из токена).
- **Логика:** `month` вне 1..12 → 400; пользователь не найден → 401; `findByUserIdAndStatusAndDateBetweenOrderByTimeAsc`
  c `YearMonth` диапазоном; маппинг в DTO.
- **Возврат:** 200 + список.
- **Побочные эффекты:** нет.

#### `toDto(userSchedules)` → `CalendarEntryDto` (static, private)
- **Назначение:** локальные `date`/`time`/`endTime`, `title`, `note`, `allDay` (true если `time==null`).
- **Возврат:** DTO.

#### `endTime(userSchedules)` → String (static, private)
- **Назначение:** локальное время конца в зоне записи; null если нет `endAt`/`timezone`.

## userController (`@RestController @RequestMapping("/user")`)
Профиль и список пользователей.

#### `me(principal)` → `ResponseEntity<UserDto>` `GET /user/me`
- **Назначение:** профиль текущего пользователя (из токена).
- **Возврат:** 200 + `UserDto`, либо 401 если не найден.

#### `getAllUsers()` → `ResponseEntity<List<UserDto>>` `GET /user`
- **Назначение:** все пользователи (маппинг `UserDto::from`).
- **Возврат:** 200 + список.

## usersController (`@RestController @RequestMapping("/users")`)
Часовой пояс.

- `record TimezoneRequest(String timezone)`.
- `record TimezoneResponse(String timezone, String status)`.

#### `setMyTimezone(principal, request)` → `ResponseEntity<TimezoneResponse>` `PUT /users/me/timezone`
- **Назначение:** сохранить зону устройства со статусом AUTO.
- **Логика:** пусто → 400; `ZoneId.of` ошибка → 400; `userService.setTimezone(..., AUTO)` → 200 + ответ.
- **Возврат:** 200 + `{timezone, status}`.

## DTO

### AuthRequest (record)
`@NotBlank String initData` — тело логина (в теле, не в query: initData — секрет).

### AuthResponse (record)
`(String token, long expiresIn, UserDto user)` — форма совпадает с `TelegramAuthResult` фронта.

### CalendarEntryDto (record, `@JsonInclude(NON_NULL)`, `@JsonIgnoreProperties(ignoreUnknown=true)`)
`(Long id, String date, String time, String endTime, String title, String note, Boolean allDay)`.

### UserDto (record)
`(long id, String firstName, String lastName, String username)`.
#### `from(userEntity)` → UserDto (static)
- **Назначение:** публичное представление; `lastName` всегда null, `id` = userId (или 0).

## CorsConfig (`@Configuration implements WebMvcConfigurer`)
#### Конструктор `CorsConfig(allowedOrigins)`
- **Параметры:** `spring.web.cors.allowed-origins` (список через запятую).

#### `addCorsMappings(registry)` → void
- **Назначение:** CORS для `/**`: `allowedOrigins` (не `*`), методы GET/POST/PUT/PATCH/DELETE/OPTIONS,
  заголовки Authorization/Content-Type/Accept, `maxAge(3600)`.

## GlobalExceptionHandler (`@RestControllerAdvice`)
Ошибки в JSON, наружу не течёт текст исключения.

#### `handleInitData(InitDataException)` → 401 `{"error":"invalid initData"}`
#### `handleValidation(MethodArgumentNotValidException)` → 400 `{"error":"invalid request"}`
#### `handleBadRequest(IllegalArgumentException)` → 400 `{"error":"bad request"}`
#### `handleLlm(LlmRequestException)` → 502 `{"error":"ai unavailable"}`
#### `handleSchedule(ScheduleException)` → 400 `{"error":"schedule incomplete"}`