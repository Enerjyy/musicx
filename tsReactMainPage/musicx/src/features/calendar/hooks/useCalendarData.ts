import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { getJson } from "../../../api/http";

/**
 * Данные календаря с бэкенда.
 *
 * Бэкенд отдаёт список записей за месяц, у каждой — дата (YYYY-MM-DD),
 * необязательное время и произвольный текст. Хук приводит ответ к
 * `Map<dateKey, CalendarEntry[]>` — это единственное, с чем работает UI:
 * ни сортировка, ни группировка на стороне компонентов не нужны.
 *
 * Запрос уходит при монтировании (каждое открытие страницы) и при смене
 * месяца. Старый запрос отменяется через AbortController, а устаревшие
 * ответы отбрасываются по requestId — иначе «прошлый» месяц мог бы
 * перезаписать текущий при быстром листании.
 */

export interface CalendarEntry {
    /** `YYYY-MM-DD`, локальная дата без времени. */
    date: string;
    /** `HH:mm` или `HH:mm:ss`. Может отсутствовать — тогда запись без времени. */
    time?: string;
    title?: string;
    note?: string;
}

/** Сырой ответ: массив либо объект с полем entries. */
export type CalendarResponse = CalendarEntry[] | { entries?: CalendarEntry[] };

export interface CalendarParams {
    /** 0-based месяц, как в Date. */
    month: number;
    year: number;
}

export type CalendarStatus = 'idle' | 'loading' | 'success' | 'error';

/** Записи, сгруппированные по `YYYY-MM-DD` и отсортированные по времени. */
export type EntriesByDate = Map<string, CalendarEntry[]>;

const pad = (value: number): string => String(value).padStart(2, '0');

const toKey = (date: Date): string =>
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;

/** Нормализует дату в `YYYY-MM-DD`, отбрасывая время и offset. */
const normalizeDateKey = (raw: string): string | null => {
    const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(raw.trim());
    if (!match) return null;
    return `${match[1]}-${match[2]}-${match[3]}`;
};

const normalizeTime = (raw?: string): string | undefined => {
    const value = raw?.trim();
    if (!value) return undefined;
    // Берём только HH:mm[:ss] — на случай, если бэкенд пришлёт полный ISO.
    const match = /(\d{2}):(\d{2})/.exec(value);
    return match ? `${match[1]}:${match[2]}` : undefined;
};

const toEntry = (raw: unknown): CalendarEntry | null => {
    if (typeof raw !== 'object' || raw === null) return null;

    const candidate = raw as Record<string, unknown>;
    const dateKey = typeof candidate.date === 'string' ? normalizeDateKey(candidate.date) : null;
    if (!dateKey) return null;

    const text = (value: unknown): string | undefined =>
        typeof value === 'string' && value.trim() ? value.trim() : undefined;

    return {
        date: dateKey,
        time: normalizeTime(typeof candidate.time === 'string' ? candidate.time : undefined),
        title: text(candidate.title),
        note: text(candidate.note),
    };
};

/** Приводит ответ бэкенда к Map по датам; битые записи пропускает. */
export const groupEntries = (response: CalendarResponse | null | undefined): EntriesByDate => {
    const raw: unknown[] = Array.isArray(response)
        ? response
        : Array.isArray(response?.entries)
          ? response.entries
          : [];

    const byDate: EntriesByDate = new Map();

    for (const item of raw) {
        const entry = toEntry(item);
        if (!entry) continue;

        const list = byDate.get(entry.date);
        if (list) list.push(entry);
        else byDate.set(entry.date, [entry]);
    }

    for (const list of byDate.values()) {
        list.sort((a, b) => (a.time ?? '99:99').localeCompare(b.time ?? '99:99'));
    }

    return byDate;
};

export const useCalendarData = ({ month, year }: CalendarParams) => {
    const [response, setResponse] = useState<CalendarResponse | null>(null);
    const [status, setStatus] = useState<CalendarStatus>('idle');
    const [error, setError] = useState<string | null>(null);
    const [reloadToken, setReloadToken] = useState(0);

    // Номер запроса: игнорируем всё, кроме ответа на последний.
    const requestIdRef = useRef(0);
    const abortRef = useRef<AbortController | null>(null);

    useEffect(() => {
        const requestId = ++requestIdRef.current;
        abortRef.current?.abort();

        const controller = new AbortController();
        abortRef.current = controller;

        setStatus('loading');
        setError(null);

        const path = `/calendar?year=${year}&month=${pad(month + 1)}`;

        void getJson<CalendarResponse>(path, { signal: controller.signal })
            .then((result) => {
                if (requestId !== requestIdRef.current) return;
                setResponse(result ?? null);
                setStatus('success');
            })
            .catch((cause: unknown) => {
                if (requestId !== requestIdRef.current) return;
                // Отмена — не ошибка, просто вышли из состояния загрузки.
                if (cause instanceof DOMException && cause.name === 'AbortError') return;

                setResponse(null);
                setStatus('error');
                setError(cause instanceof Error ? cause.message : String(cause));
            });

        return () => controller.abort();
    }, [month, year, reloadToken]);

    const reload = useCallback(() => {
        setReloadToken((token) => token + 1);
    }, []);

    const entriesByDate = useMemo(() => groupEntries(response), [response]);

    return { entriesByDate, status, error, reload, isLoading: status === 'loading' };
};

/** Записи на конкретный день — пустой массив, если записей нет. */
export const entriesForDate = (
    entriesByDate: EntriesByDate,
    date: Date | null,
): CalendarEntry[] => (date ? entriesByDate.get(toKey(date)) ?? [] : []);