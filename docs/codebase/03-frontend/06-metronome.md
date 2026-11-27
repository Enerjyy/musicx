# 06 · frontend — метроном

Файлы: `features/metronome/hooks/catOnButton.tsx`, компоненты `ButtonBPM`, `SlidebarBPM`, `BeatMarkers`,
`VerticalScrollingInput`.

## catOnButton.tsx (hook `useCatSlider`)
Метроном на Tone.js: кот-слайдер слева-направо, сэмплы тика, BPM, размер.

- `catImages` — 3 картинки кота.
- Состояние: `currentIndex`, `right`, `isRunning`, `BPMValue` (120), `currentBeat`, `numerator` (4),
  `denominator` (4); `sampleRef: Tone.Sampler`.

#### `useCatSlider()` → объект
- **Эффект 1 (mount):** `Tone.Volume(-10)` + `Tone.Sampler({A1: tick, G1: forceTick})`; cleanup `dispose`.
- **Эффект 2 (интервал):** при `isRunning` `setInterval` с периодом `60/BPM*4/denominator*1000/2`;
  двигает `currentIndex` (bounce по `catImages`), на каждом сдвиге (кроме индекса 1) увеличивает `currentBeat`
  по `numerator` и триггерит `G1` на downbeat, иначе `A1`.
- `toggleSlider()` → async: при старте `unlockAudio()`, затем переключает `isRunning`.
- **Возврат:** `{currentImage, toggleSlider, BPMValue, setBPMValue, currentBeat, numerator, setNumerator,
  denominator, setDenominator}`.

## ButtonBPM.tsx
#### `MetronomeButton({currentImage, toggleSlider})` → JSX
- **Назначение:** кнопка-кот (старт/стоп), `aria-label`.

## SlidebarBPM.tsx
#### `MetronomeSlider({BPMValue, setBPMValue})` → JSX
- **Назначение:** слайдер BPM (20–340) + числовое значение.

## BeatMarkers.tsx
#### `BeatMarkers({totalBeats, currentBeat})` → JSX
- **Назначение:** точки-индикаторы долей, активная подсвечивается.

## VerticalScrollingInput.tsx
#### `ScrollingInput({min, max, value, setValue, label})` → JSX
- **Назначение:** числовое поле с ограничением `max`.
- **Логика `handleChange`:** пусто → 60; `numericValue <= max` → `setValue`; иначе игнор.