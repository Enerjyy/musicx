import type {JSX} from "react";
import type {CalendarEntry} from "../hooks/useCalendarData";
import styles from './styles/Calendar.module.css';

interface DayEntriesProps {
    entries: CalendarEntry[];
    /** Записей нет, потому что день пустой — показываем подсказку. */
    loading: boolean;
}

/**
 * Записи выбранного дня: время + заголовок + заметка.
 * Отсутствие времени не ломает строку — просто не рисуем колонку.
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
            {entries.map((entry, index) => (
                <li key={`${entry.date}-${entry.time ?? 'x'}-${index}`} className={styles.entry}>
                    {entry.time && <span className={styles.entryTime}>{entry.time}</span>}

                    <div className={styles.entryBody}>
                        {entry.title && <span className={styles.entryTitle}>{entry.title}</span>}
                        {entry.note && <span className={styles.entryNote}>{entry.note}</span>}
                    </div>
                </li>
            ))}
        </ul>
    );
};

export default DayEntries;