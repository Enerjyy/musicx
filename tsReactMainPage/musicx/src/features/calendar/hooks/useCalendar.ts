import { useCallback, useMemo, useState } from "react";

/**
 * Логика календаря: сетка дней для отображаемого месяца, выбранная дата и
 * переходы между месяцами. Чистая логика вынесена сюда, чтобы компонент
 * занимался только разметкой.
 *
 * Даты работают в локальном времени (не UTC): `new Date(year, month, day)`
 * даёт полночь по локальному времени, поэтому `getDay()` и сравнение
 * «сегодня/вчера» не уезжают на сдвиг часового пояса.
 */

export const MONTHS = [
    "January",
    "February",
    "March",
    "April",
    "May",
    "June",
    "July",
    "August",
    "September",
    "October",
    "November",
    "December",
] as const;

/** Пн–Вс: неделя начинается с понедельника. */
export const WEEKDAYS = ["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"] as const;

export interface CalendarDay {
    date: Date;
    /** Локальный `YYYY-MM-DD` — стабильный ключ, не зависящий от таймзоны. */
    key: string;
    day: number;
    inMonth: boolean;
    isToday: boolean;
}

const pad = (value: number): string => String(value).padStart(2, "0");

export const toKey = (date: Date): string =>
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;

export const startOfDay = (date: Date): Date =>
    new Date(date.getFullYear(), date.getMonth(), date.getDate());

export const isSameDay = (a: Date, b: Date): boolean => toKey(a) === toKey(b);

/**
 * Собирает сетку месяца: blank-смещение до 1-го числа + полные недели,
 * чтобы ряд с 31-м числом не «прыгал» между месяцами.
 */
const buildGrid = (year: number, month: number, todayKey: string): CalendarDay[] => {
    const first = new Date(year, month, 1);
    // getDay(): 0 = воскресенье. Приводим к понедельнику.
    const offset = (first.getDay() + 6) % 7;
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const cells = Math.ceil((offset + daysInMonth) / 7) * 7;

    return Array.from({ length: cells }, (_, index) => {
        const date = new Date(year, month, 1 - offset + index);
        const key = toKey(date);

        return {
            date,
            key,
            day: date.getDate(),
            inMonth: date.getMonth() === month,
            isToday: key === todayKey,
        };
    });
};

export const useCalendar = () => {
    const today = useMemo(() => startOfDay(new Date()), []);
    const [selected, setSelected] = useState<Date | null>(today);
    const [view, setView] = useState(() => ({
        year: today.getFullYear(),
        month: today.getMonth(),
    }));

    const todayKey = toKey(today);

    const days = useMemo(
        () => buildGrid(view.year, view.month, todayKey),
        [view.year, view.month, todayKey],
    );

    const selectedKey = selected ? toKey(selected) : null;

    /** Выбор дня: клик по соседнему месяцу заодно переводит на него. */
    const select = useCallback((date: Date) => {
        const next = startOfDay(date);
        setSelected(next);
        setView({ year: next.getFullYear(), month: next.getMonth() });
    }, []);

    const goPrev = useCallback(() => {
        setView(({ year, month }) => {
            const next = new Date(year, month - 1, 1);
            return { year: next.getFullYear(), month: next.getMonth() };
        });
    }, []);

    const goNext = useCallback(() => {
        setView(({ year, month }) => {
            const next = new Date(year, month + 1, 1);
            return { year: next.getFullYear(), month: next.getMonth() };
        });
    }, []);

    const goToday = useCallback(() => {
        const now = startOfDay(new Date());
        setSelected(now);
        setView({ year: now.getFullYear(), month: now.getMonth() });
    }, []);

    const isCurrentMonth =
        view.year === today.getFullYear() && view.month === today.getMonth();

    return {
        days,
        view,
        selected,
        selectedKey,
        today,
        todayKey,
        isCurrentMonth,
        select,
        goPrev,
        goNext,
        goToday,
    };
};