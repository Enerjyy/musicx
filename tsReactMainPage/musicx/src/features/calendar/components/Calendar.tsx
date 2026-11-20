import type {JSX} from "react";
import { MONTHS, useCalendar } from "../hooks/useCalendar";
import { useCalendarData } from "../hooks/useCalendarData";
import MonthSwitcher from "./MonthSwitcher";
import DayGrid from "./DayGrid";
import styles from './styles/Calendar.module.css';

/**
 * Календарь на месяц. При открытии страницы и при смене месяца уходит
 * GET /calendar на бэкенд (см. useCalendarData) — данные пока никак не
 * влияют на сетку, хук только поднимает статус запроса.
 */
const Calendar = (): JSX.Element => {
    const {
        days,
        view,
        selected,
        selectedKey,
        isCurrentMonth,
        select,
        goPrev,
        goNext,
        goToday,
    } = useCalendar();

    // Запрос уходит на каждое открытие страницы и на каждую смену месяца.
    const { status, error, reload } = useCalendarData({ month: view.month, year: view.year });

    const monthLabel = `${MONTHS[view.month]} ${view.year}`;
    const selectedLabel = selected
        ? selected.toLocaleDateString(undefined, { day: 'numeric', month: 'long' })
        : '—';

    return (
        <div className={styles.panel}>
            <div className={styles.card}>
                <MonthSwitcher
                    monthLabel={monthLabel}
                    onPrev={goPrev}
                    onNext={goNext}
                    onToday={goToday}
                    showToday={!isCurrentMonth}
                />

                <DayGrid days={days} selectedKey={selectedKey} onSelect={select} />
            </div>

            <div className={styles.meta}>
                <p className={styles.caption}>
                    Selected <span className={styles.captionValue}>{selectedLabel}</span>
                </p>

                <p className={styles.status} aria-live="polite">
                    {status === 'loading' && 'Loading…'}
                    {status === 'success' && 'Loaded'}
                    {status === 'error' && (
                        <>
                            <span className={styles.statusError} title={error ?? undefined}>
                                Load failed
                            </span>{' '}
                            <button type="button" className={styles.retry} onClick={reload}>
                                Retry
                            </button>
                        </>
                    )}
                </p>
            </div>
        </div>
    );
};

export default Calendar;