# 04 · frontend — аудио (unlockAudio)

Файл: `src/audio/unlockAudio.ts`.

Браузеры (и Telegram WebView на iOS) держат `AudioContext` в suspended до реального жеста пользователя.
Tone создаёт контекст на импорте (вне жеста), поэтому первый жест должен его разблокировать.

- `let unlockPromise: Promise<void> | null` — кэш текущей попытки (не мемоизирует провал).

#### `unlock()` → `Promise<boolean>` (private)
- **Назначение:**Attempt разблокировки.
- **Логика:** если `Tone.getContext().state !== 'running'` → `Tone.start()`; если всё ещё не running и есть
  `resume()` → `resume()`; ошибки → `console.error` (не пробрасываются).
- **Возврат:** `state === 'running'`.

#### `unlockAudio()` → `Promise<void>`
- **Назначение:** запустить/дождаться разблокировки; безопасно вызывать на каждый жест.
- **Логика:** уже running → `Promise.resolve`; иначе создать `unlockPromise` (сбросить при неудаче, чтобы
  следующий жест мог повторить).

#### `installAudioUnlock()` → `() => void`
- **Назначение:** зарегистрировать одноразовые слушатели `pointerdown/mousedown/touchstart/keydown`
  (capture, passive), вызывающие `unlockAudio`.
- **Возврат:** функция очистки (снимает слушатели). Вызывается один раз в `main.tsx`.