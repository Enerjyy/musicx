# 08 · frontend — страницы (pages)

Файлы: `src/pages/CalendarPage.tsx`, `MetronomePage.tsx`, `TunerPage.tsx`. Тонкие обёртки над фичами.

## CalendarPage.tsx
#### `CalendarPage()` → JSX
- **Назначение:** рендерит `Calendar` (вся логика — в `features/calendar`).

## MetronomePage.tsx
#### `MetronomePage()` → JSX
- **Назначение:** собирает `useCatSlider` + `MetronomeButton` + `MetronomeSlider` + `BeatMarkers` +
  три `ScrollingInput` (BPM 20–340, Num 1–16, Den 1–16).

## TunerPage.tsx
#### `TunerPage()` → JSX
- **Назначение:** `useCountingHZ` + `CheckerHZ` (pitch/note/isActive/toggleMic).