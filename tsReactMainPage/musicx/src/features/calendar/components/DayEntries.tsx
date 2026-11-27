import type {JSX} from "react";
import type {CalendarEntry} from "../hooks/useCalendarData";
import styles from './styles/Calendar.module.css';

interface DayEntriesProps {
    entries: CalendarEntry[];
    /** Записей нет, потому что день пустой — показываем подсказку. */
    loading: boolean;
}

/**
 * Длительность «начало–конец» в человекочитаемом виде: «1 ч 30 мин» / «45 мин».
 * Считается из уже нормализованных строк HH:mm; если конец отсутствует,
 * невалиден или меньше начала — длительность не показываем.
 */
const formatDuration = (time?: string, endTime?: string): string | undefined => {
    if (!time || !endTime) return undefined;

    const toMinutes = (value: string): number | null => {
        const match = /^(\d{2}):(\d{2})$/.exec(value);
        return match ? Number(match[1]) * 60 + Number(match[2]) : null;
    };

    const start = toMinutes(time);
    const end = toMinutes(endTime);
    if (start === null || end === null || end <= start) return undefined;

    const minutes = end - start;
    const hours = Math.floor(minutes / 60);
    const rest = minutes % 60;
    if (hours === 0) return `${rest} мин`;
    if (rest === 0) return `${hours} ч`;
    return `${hours} ч ${rest} мин`;
};

/**
 * Записи выбранного дня: время (начало–конец и длительность) + заголовок + заметка.
 * Безвременные записи помечаются плашкой «Весь день», отсутствие времени
 * у обычных записей не ломает строку — просто не рисуем колонку.
 */
const DayEntries = ({ entries, loading }: DayEntriesProps): JSX.Element => {
    if (loading) {
        return <p className={styles.entriesEmpty}>Loading…</p>;
    }

    if (entries.length === 0) {
        return <p className={styles.entriesEmpty}>No entries</p>;
    }

    return (
        <ul className={styles.entries}>
            {entries.map((entry, index) => {
                // id с бэкенда делает ключ стабильным при переносах записей;
                // index — только запасной вариант для записей без id (тесты, старый бэкенд).
                const key = entry.id !== undefined
                    ? `${entry.date}-${entry.id}`
                    : `${entry.date}-${entry.time ?? 'x'}-${index}`;
                const duration = formatDuration(entry.time, entry.endTime);

                return (
                    <li key={key} className={styles.entry}>
                        {entry.allDay && <span className={styles.entryAllDay}>Весь день</span>}
                        {!entry.allDay && entry.time && (
                            <span className={styles.entryTime}>
                                {entry.endTime ? `${entry.time}–${entry.endTime}` : entry.time}
                                {duration && <span className={styles.entryDuration}> · {duration}</span>}
                            </span>
                        )}

                        <div className={styles.entryBody}>
                            {entry.title && <span className={styles.entryTitle}>{entry.title}</span>}
                            {entry.note && <span className={styles.entryNote}>{entry.note}</span>}
                        </div>
                    </li>
                );
            })}
        </ul>
    );
};

export default DayEntries;
