# 03 · frontend — Telegram (auth/session)

Файлы: `src/telegram/auth.ts`, `session.ts`, `useTelegram.ts`, `types.ts`.

## types.ts
Минимальные типы Telegram WebApp (без внешних SDK).

- `interface TelegramWebApp` — используемое подмножество: `ready`, `expand`, `requestFullscreen?`,
  `exitFullscreen?`, `isFullscreen?`, `viewportHeight?`, `viewportStableHeight?`, `safeAreaInset?`,
  `contentSafeAreaInset?`, `colorScheme?`, `themeParams?`, `platform?`, `version?`, `initData?`,
  `initDataUnsafe?`, `onEvent`, `offEvent`.
- `type TelegramEvent` — `activated | viewportChanged | fullscreenChanged | fullscreenFailed | themeChanged`.
- `declare global { interface Window { Telegram?: { WebApp?: TelegramWebApp } } }`.

#### `getTelegramWebApp()` → `TelegramWebApp | undefined`
- **Назначение:** `window.Telegram?.WebApp` (undefined вне браузера).

#### `isTelegramMiniApp()` → boolean
- **Назначение:** true, если есть `initData`.

## auth.ts
Обмен `initData` на JWT; кэш и «в полёте» промис.

- `interface TelegramUser { id, firstName, lastName, username }`.
- `interface TelegramAuthResult { token, user }`.
- `let cached`, `let inFlight` —模块-level кэш/дедуп.

#### `readAuthResult(raw)` → `TelegramAuthResult` (private)
- **Назначение:** разобрать ответ (объект или обёртка `data`), проверить `token`, собрать `user`.
- **Ошибки:** `HttpError` (не объект / нет token).

#### `loginWithTelegram()` → `Promise<TelegramAuthResult>`
- **Назначение:** залогиниться по `initData` (идемпотентно: кэш + общий `inFlight`).
- **Логика:** кэш → возврат; `inFlight` → возврат; не Mini App → `Error('not a Mini App')`;
  `POST /auth/telegram {initData}`; `readAuthResult`; `setToken`; fire-and-forget `syncTimezone`.
- **Ошибки:** `HttpError` при не-2xx.
- **Побочные эффекты:** токен в память, синхронизация зоны.

#### `ensureLoggedIn()` → `Promise<TelegramAuthResult | null>`
- **Назначение:** заранее начать логин; вне Telegram → `null` (не бросает).

#### `getCachedUser()` → `TelegramUser | null`
- **Назначение:** пользователь из кэша.

#### `syncTimezone(token, timeZone?)` → `Promise<boolean>`
- **Назначение:** `PUT /users/me/timezone {timezone}` (зона устройства по умолчанию).
- **Логика:** `Intl` зона; невалидная/сеть → false; успех → `res.ok`. Никогда не бросает.

#### `getTokenFromCache()` → `string | null`
- **Назначение:** токен из `token.ts`.

#### `clearSession()` → void
- **Назначение:** сбросить кэш и токен (для повторного логина при 401).

## session.ts
Собирает аутентификацию: стартовый логин + реакция на 401.

- `let installed` — защита от повторной установки.

#### `installSession()` → void
- **Назначение:** один раз подключить `setAuthReady(ensureLoggedIn())` и `setUnauthorizedHandler(...)`.
- **Логика 401:** `clearSession()` → `loginWithTelegram()` → true/false (один повтор, без зацикливания).
- **Примечание:** вне Mini App — no-op.

## useTelegram.ts
Бутстрап Mini App: fullscreen + insets.

- `FALLBACK_HEADER_TOP = 46` — запасная высота плавающей шапки Telegram.

#### `useTelegram()` → boolean
- **Назначение:** `ready()` + `expand()` + `requestFullscreen()`; синхронизация CSS-переменных
  `--tg-inset-top/bottom`, `--tg-safe-left/right`, `--tg-viewport-height`; возвращает `isMiniApp`.
- **Логика:** `getTelegramWebApp`; если `platform==='unknown'` или нет `initData` → не Mini App (выход);
  `setIsMiniApp(true)`; `requestFullscreen` (с повтором на `activated`/первом `pointerdown`);
  `syncInsets` через `safeAreaInset`/`contentSafeAreaInset`; подписки `viewportChanged`/`fullscreenChanged`/`activated`
  с cleanup.
- **Возврат:** `isMiniApp`.