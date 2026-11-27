# 02 · frontend — слой API

Файлы: `src/api/http.ts`, `src/api/token.ts`. (`src/api/generated/api.ts` — генерируемый Orval, не разбирается.)

## token.ts
Хранит токен и «готовность» логина. Вынесен отдельно, чтобы разорвать цикл `http.ts ↔ auth.ts`.

- `let token: string | null` — токен в памяти (не в localStorage: WebView чистит хранилище, initData приходит каждый раз).
- `let ready: Promise<unknown>` — разрешается по завершении стартового логина.

#### `getToken()` → `string | null`
- **Назначение:** текущий токен.

#### `setToken(value)` → void
- **Назначение:** записать/сбросить токен.

#### `getAuthReady()` → `Promise<unknown>`
- **Назначение:** промис готовности логина (http.ts ждёт его перед первым запросом).

#### `setAuthReady(promise)` → void
- **Назначение:** задать промис готовности.

## http.ts
Тонкая обёртка над `fetch`: базовый URL, JWT, разбор ошибок, один ретрай на 401.

- `DEV_FALLBACK = 'http://localhost:8081'`; `API_BASE_URL` из `VITE_API_URL` (без хвостовых `/`) или fallback.

#### `class HttpError extends Error`
- Поля `status:number`, `url:string`; конструктор `(message, status, url)`; `name='HttpError'`.

#### `buildUrl(path)` → string
- **Назначение:** `API_BASE_URL + path` (с ведущим `/`).

#### `isAbort(error)` → boolean (private)
- **Назначение:** `DOMException` с `name==='AbortError'` (штатная отмена).

#### `setUnauthorizedHandler(handler)` → void
- **Назначение:** задать обработчик 401 (ставит `session.ts`).

#### `requestOnce<T>(path, init, url)` → `Promise<T>` (private)
- **Назначение:** один `fetch` с разбором.
- **Логика:** сеть/abort → `HttpError(0)`; не-2xx → `HttpError(status)`; 204/205/304/пусто → `undefined`;
  невалидный JSON → `HttpError('Response is not valid JSON')`.

#### `buildHeaders(options)` → `Record<string,string>` (private)
- **Назначение:** `Accept` + `Authorization: Bearer` (если не `skipAuth` и есть токен) + `options.headers`.

#### `getJson<T>(path, options)` → `Promise<T>`
- **Назначение:** GET c JSON.
- **Логика:** `await getAuthReady()`; `requestOnce`; при 401 (и есть `unauthorizedHandler`, не `skipAuth`) —
  один `unauthorizedHandler()` и повтор с новым токеном.

#### `postJson<T,B>(path, body, options)` → `Promise<T>`
- **Назначение:** POST c JSON-телом; та же логика 401/повтора, что у `getJson`.

- `interface RequestOptions { signal?, skipAuth?, headers? }`.
- `type UnauthorizedHandler = () => Promise<boolean>`.