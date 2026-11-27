# 01 · frontend — загрузка приложения

Пакет `tsReactMainPage/musicx`. Точки входа, конфигурация сборки.

## main.tsx
Точка входа браузера.
- `installAudioUnlock()` — регистрирует одноразовые слушатели (pointerdown/mousedown/touchstart/keydown),
  разблокирующие AudioContext на первом жесте (иначе piano/метроном молчат).
- `installSession()` — стартовый логин по initData + реакция на 401 (в обычном браузере — no-op).
- `createRoot(document.getElementById('root')!).render(<App />)` — монтирование React.

## App.tsx
Корневой компонент. Карта вкладок `PAGES = {tuner, metronome, calendar}`; `PageKey`, `PAGE_KEYS`.

#### `App()` → JSX
- **Назначение:** оболочка с шапкой, активной страницей и навигацией.
- **Состояние:** `currentPage` (useState, default `tuner`).
- **Логика:** `useTelegram()` → класс `app--tg`; рендер `PAGES[currentPage].component`; кнопки навигации
  с `aria-current`.
- **Возврат:** JSX.

## vite.config.ts
- Плагин `@vitejs/plugin-react`; `base: '/'`; build → `dist`/`assets`, sourcemap off.
- `server.allowedHosts`: ngrok-домен и `musicxapp.duckdns.org`.

## orval.config.ts
Генератор клиента из OpenAPI (`http://localhost:8080/v3/api-docs`) → `./src/api/generated/api.ts`,
client `fetch`, baseUrl `http://localhost:8080`. (Файл `api/generated/api.ts` — генерируемый, не разбирается.)

## eslint.config.js
Конфигурация ESLint (флакон проекта; построчно не разбирается).

## package.json (корень)
`react ^19.3.1`, `@types/react ^19.3.0`. (Зависимости фронта — в `tsReactMainPage/musicx/package.json`.)