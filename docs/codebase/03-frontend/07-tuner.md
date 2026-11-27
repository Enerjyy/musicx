# 07 · frontend — тюнер

Файлы: `features/tuner/hooks/useCountingHZ.ts`, компоненты `CheckerHZ`, `Piano`.

## useCountingHZ.ts
Детекция высоты тона с микрофона (YIN из `pitchfinder`) + сглаживание.

Константы: `MIN_FREQUENCY=65.41` (C2), `MAX_FREQUENCY=1800` (~A6), `UI_REFRESH_MS=70`,
`SMOOTHING_SIZE=5`, `ANALYSER_SIZE=4096`, `SIGNAL_LOST_FRAMES=4`, `MIN_RMS=0.0003`, `MIC_GAIN=32`,
`NORMALIZE_PEAK=1`.
Refs: `streamRef`, `sourceRef`, `gainRef`, `analyserRef`, `bufferRef`, `requestRef`, `activeRef`,
`startingRef`, `aliveRef`, `lastUpdateRef`, `recentFreqsRef`, `missedFramesRef`.

#### `teardownAudio(resetState)` → void (useCallback, private)
- **Назначение:** остановить RAF, отключить source/gain, остановить треки, сбросить буфер/сглаживание;
  при `resetState` — `isActive=false`, `pitch/note=null`.

#### `toggleMic()` → `Promise<void>` (useCallback)
- **Назначение:** включить/выключить микрофон.
- **Логика:** защита от повторного старта (`startingRef`); выключение → `teardownAudio(true)`;
  `audioSession.type='play-and-record'` (iOS); `getUserMedia` (с fallback на `{audio:true}` при
  Overconstrained/NotSupported/Type); `Tone.start()`; построение графа `source→gain(32)→analyser`;
  `YIN({sampleRate, threshold:0.15})`; `analyze()` (RAF): каждые 70 мс считает RMS/peak, нормализует,
  `detectPitch`, медиана по 5 значениям, `Tone.Frequency(median).toNote()`; потеря сигнала ≥4 кадров → гасит.
- **Ошибки:** останавливает треки, `console.error`, `teardownAudio(true)`.

#### `useEffect` (mount/unmount)
- **Назначение:** `aliveRef=true`; cleanup → `aliveRef=false`, `teardownAudio(false)`.

- **Возврат:** `{note, pitch, isActive, toggleMic}`.

## CheckerHZ.tsx
- Константы `KEYBOARD_START_OCTAVE=2`, `KEYBOARD_OCTAVES=4`.
#### `CheckerHZ({pitch, note, isActive, toggleMic})` → JSX
- **Назначение:** `Piano` (2..5 октавы, подсветка `detectedNote`) + кнопка микрофона + табло ноты/Гц.

## Piano.tsx
Пианино на Tone.js с мультитачем, залипанием (двойной тап) и автопрокруткой к слышимой ноте.

- `NOTE_NAMES` (12), `BLACK_SIZE_RATIO=0.62`, `DOUBLE_TAP_MS=320`.
- Типы: `KeyKind`, `PianoKey`, `BlackPianoKey`, `Keyboard`.

#### `buildKeyboard(startOctave, octaveCount)` → `Keyboard` (private)
- **Назначение:** построить белые/чёрные клавиши с процентами позиционирования; завершающая C.

#### `Piano({startOctave=1, octaves=6, detectedNote=null})` → JSX
- **Состояние/refs:** `pressedNotes`, `latchedNote`, `synthRef`, `heldPointersRef`, `soundingRef`,
  `latchedRef`, `lastTapRef`, `viewportRef`, `keyRefsRef`, `detectedNoteRef`.
- `centerKey(targetNote, behavior)` (private) — прокрутка к клавише.
- `useLayoutEffect` — центр на C4 при открытии.
- `useEffect(detectedNote)` — плавно подвести к слышимой ноте (respect `prefers-reduced-motion`).
- `useEffect(resize)` — вернуть текущую ноту в центр.
- `getSynth()` (useCallback) — ленивое создание `Tone.PolySynth(Tone.Synth, triangle)`.
- `sync()` (useCallback) — привести звук/подсветку к желаемому состоянию (идемпотентно).
- `ensureAudio(shortTapNote?)` (useCallback) — `unlockAudio().then(...)` + короткий тап.
- `setLatch(note)` (useCallback) — залипшая нота.
- `handlePointerDown(event, note)` — мультитач/двойной тап/залипание.
- `useEffect` (pointerup/cancel/blur/visibilitychange/pointerdown вне клавиш) — отпускание/ silenceAll.
- `registerKey(note)` — ref-колбэк.
- `keyClassName(key, isC)` — классы (white/black/isC/isPressed/isLatched/isDetected).
- **Возврат:** JSX (белые + чёрные клавиши-кнопки, `data-piano-key`, `aria-pressed`).