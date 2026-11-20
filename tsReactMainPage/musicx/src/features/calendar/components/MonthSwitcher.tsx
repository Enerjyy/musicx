import type {JSX} from "react";
import styles from './styles/Calendar.module.css';

interface MonthSwitcherProps {
    monthLabel: string;
    onPrev: () => void;
    onNext: () => void;
    onToday: () => void;
    showToday: boolean;
}

/**
 * Шапка календаря: месяц + год, стрелки и кнопка «сегодня».
 * `showToday` скрывает сброс на текущий месяц, когда он и так открыт.
 */
const MonthSwitcher = ({
    monthLabel,
    onPrev,
    onNext,
    onToday,
    showToday,
}: MonthSwitcherProps): JSX.Element => {
    return (
        <div className={styles.header}>
            <button
                type="button"
                className={styles.arrow}
                onClick={onPrev}
                aria-label="Previous month"
            >
                <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M15 5l-7 7 7 7" />
                </svg>
            </button>

            <h2 className={styles.month}>{monthLabel}</h2>

            <button
                type="button"
                className={styles.arrow}
                onClick={onNext}
                aria-label="Next month"
            >
                <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M9 5l7 7-7 7" />
                </svg>
            </button>

            <button
                type="button"
                className={styles.today + (showToday ? '' : ` ${styles.todayHidden}`)}
                onClick={onToday}
            >
                Today
            </button>
        </div>
    );
};

export default MonthSwitcher;