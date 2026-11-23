import type {JSX} from "react";
import { MONTHS, useCalendar } from "../hooks/useCalendar";
import { entriesForDate, useCalendarData } from "../hooks/useCalendarData";
import MonthSwitcher from "./MonthSwitcher";
import DayGrid from "./DayGrid";
import DayEntries from "./DayEntries";
import styles from './styles/Calendar.module.css';

/**
 * Календарь на месяц.
 *
 * При открытии страницы и при смене месяца уходит GET /calendar, бэкенд
 * отдаёт записи за месяц. Дни с записями помечаются точкой, записи
 * выбранного дня показываются списком под сеткой.
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
    const { entriesByDate, status, error, reload, isLoading } = useCalendarData({
        month: view.month,
        year: view.year,
    });

    const markedKeys = new Set(entriesByDate.keys());
    const selectedEntries = entriesForDate(entriesByDate, selected);

    const monthLabel = `${MONTHS[view.month]} ${view.year}`;
    const selectedLabel = selected
        ? selected.toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' })
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

                <DayGrid
                    days={days}
                    selectedKey={selectedKey}
                    markedKeys={markedKeys}
                    onSelect={select}
                />
            </div>

            <section className={styles.details} aria-label="Selected day">
                <h3 className={styles.detailsTitle}>{selectedLabel}</h3>
                <DayEntries entries={selectedEntries} loading={isLoading} />
            </section>

            <p className={styles.status} aria-live="polite">
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
    );
};

export default Calendar;