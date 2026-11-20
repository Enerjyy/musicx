import type {JSX} from "react";
import { WEEKDAYS, type CalendarDay } from "../hooks/useCalendar";
import styles from './styles/Calendar.module.css';

interface DayGridProps {
    days: CalendarDay[];
    selectedKey: string | null;
    onSelect: (date: Date) => void;
}

/**
 * Сетка дней. Каждое число — отдельная кнопка, соседние месяцы приглушены,
 * но остаются кликабельными (клик по ним переводит на их месяц).
 */
const DayGrid = ({ days, selectedKey, onSelect }: DayGridProps): JSX.Element => {
    return (
        <div className={styles.grid} role="group" aria-label="Days">
            {WEEKDAYS.map((label) => (
                <div key={label} className={styles.weekday} aria-hidden="true">
                    {label}
                </div>
            ))}

            {days.map(({ date, key, day, inMonth, isToday }) => {
                const isSelected = key === selectedKey;

                return (
                    <button
                        key={key}
                        type="button"
                        className={
                            styles.day +
                            (inMonth ? '' : ` ${styles.dayOutside}`) +
                            (isToday ? ` ${styles.dayToday}` : '') +
                            (isSelected ? ` ${styles.daySelected}` : '')
                        }
                        onClick={() => onSelect(date)}
                        aria-pressed={isSelected}
                        aria-current={isToday ? 'date' : undefined}
                        aria-label={
                            date.toLocaleDateString(undefined, {
                                weekday: 'long',
                                day: 'numeric',
                                month: 'long',
                                year: 'numeric',
                            })
                        }
                    >
                        {day}
                    </button>
                );
            })}
        </div>
    );
};

export default DayGrid;