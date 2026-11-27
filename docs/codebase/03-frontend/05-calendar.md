# 05 · frontend — календарь

Файлы: `features/calendar/hooks/useCalendar.ts`, `useCalendarData.ts`, компоненты `Calendar`, `DayGrid`,
`DayEntries`, `MonthSwitcher`.

## useCalendar.ts
Чистая логика сетки месяца, выбранной даты и переходов.

- Константы: `MONTHS` (12), `WEEKDAYS` (`Mo`…`Su`, неделя с понедельника).
- `interface CalendarDay { date, key, day, inMonth, isToday }`.

#### `toKey(date)` → string
- **Назначение:** локальный `YYYY-MM-DD` (стабильный ключ, не зависит от таймзоны).

#### `startOfDay(date)` → Date
- **Назначение:** локальная полночь.

#### `isSameDay(a, b)` → boolean
- **Назначение:** сравнение по `toKey`.

#### `buildGrid(year, month, todayKey)` → `CalendarDay[]` (private)
- **Назначение:** сетка месяца: blank-смещение до 1-го + полные недели (ряд с 31-м не «прыгает»).
- **Логика:** `offset = (first.getDay()+6)%7`; `cells = ceil((offset+daysInMonth)/7)*7`.

#### `useCalendar()` → объект
- **Состояние:** `selected` (default сегодня), `view {year, month}`, `today` (useMemo).
- **Логика:** `days` (useMemo `buildGrid`); `select` (клик по соседнему месяцу переводит на него);
  `goPrev/goNext/goToday`; `isCurrentMonth`.
- **Возврат:** `{days, view, selected, selectedKey, today, todayKey, isCurrentMonth, select, goPrev, goNext, goToday}`.

## useCalendarData.ts
Загрузка записей месяца с бэкенда и приведение к `Map<dateKey, CalendarEntry[]>`.

- `interface CalendarEntry { date, time?, endTime?, id?, allDay?, title?, note? }`.
- `type CalendarResponse = CalendarEntry[] | { entries?: CalendarEntry[] }`.
- `interface CalendarParams { month, year }` (month 0-based).
- `type CalendarStatus = 'idle'|'loading'|'success'|'error'`.
- `type EntriesByDate = Map<string, CalendarEntry[]>`.

#### `pad(value)` → string (private)
- **Назначение:** 2-значное дополнение.

#### `toKey(date)` → string (private)
- **Назначение:** локальный `YYYY-MM-DD`.

#### `normalizeDateKey(raw)` → `string | null` (private)
- **Назначение:** `YYYY-MM-DD` из строки (отбрасывает время/offset); не валидна → null.

#### `normalizeTime(raw?)` → `string | undefined` (private)
- **Назначение:** `HH:mm` из строки (на случай полного ISO).

#### `toEntry(raw)` → `CalendarEntry | null` (private)
- **Назначение:** нормализовать одну запись; нет даты → null.

#### `groupEntries(response)` → `EntriesByDate`
- **Назначение:** массив/объект → `Map` по датам, сортировка по времени; битые записи пропускаются.

#### `useCalendarData({month, year})` → объект
- **Состояние:** `response`, `status`, `error`, `reloadToken`; `requestIdRef`, `abortRef`.
- **Логика:** `useEffect` при монтировании/смене месяца: отмена прошлого запроса, `getJson('/calendar?year=&month=')`,
  проверка `requestId` (устаревшие ответы отбрасываются), `AbortError` — не ошибка.
- **Возврат:** `{entriesByDate, status, error, reload, isLoading}`.

#### `entriesForDate(entriesByDate, date)` → `CalendarEntry[]`
- **Назначение:** записи на день (пустой массив, если нет).

## Компоненты

### Calendar.tsx
#### `Calendar()` → JSX
- **Назначение:** контейнер: `useCalendar` + `useCalendarData`, `markedKeys`, `selectedEntries`,
  `MonthSwitcher` + `DayGrid` + `DayEntries` + статус/Retry.

### DayGrid.tsx
#### `DayGrid({days, selectedKey, markedKeys, onSelect})` → JSX
- **Назначение:** сетка дней (кнопки), приглушение соседнего месяца, точка-маркер наличия записей,
  `aria-pressed`/`aria-current`/`aria-label`.

### DayEntries.tsx
#### `formatDuration(time?, endTime?)` → `string | undefined` (private)
- **Назначение:** «1 ч 30 мин» / «45 мин»; невалидно/конец ≤ начала → undefined.
#### `DayEntries({entries, loading})` → JSX
- **Назначение:** список записей дня (время/длительность/заголовок/заметка, плашка «Весь день»);
  loading/пусто → подсказки; стабильный ключ по `id`.

### MonthSwitcher.tsx
#### `MonthSwitcher({monthLabel, onPrev, onNext, onToday, showToday})` → JSX
- **Назначение:** шапка «месяц + год», стрелки, кнопка Today (скрывается, когда открыт текущий месяц).