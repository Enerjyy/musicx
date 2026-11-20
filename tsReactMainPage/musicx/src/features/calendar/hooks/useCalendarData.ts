import { useCallback, useEffect, useRef, useState } from "react";
import { getJson } from "../../../api/http";

/**
 * Данные календаря с бэкенда.
 *
 * Запрос уходит при монтировании (то есть при каждом открытии страницы
 * календаря) и повторно — при смене месяца. Старый запрос отменяется через
 * AbortController, чтобы ответ «прошлого» месяца не перезаписал текущий.
 *
 * Бэкенд пока не отдаёт /calendar — эндпоинт добавишь ты. До тех пор хук
 * честно покажет ошибку, а не будет молча делать вид, что всё хорошо.
 */

/** Ответ бэкенда. Формат свободный — под твой контроллер. */
export interface CalendarResponse {
    /** Отметки по дням: ключ — `YYYY-MM-DD`, значение — что угодно. */
    days?: Record<string, unknown>;
    [key: string]: unknown;
}

export interface CalendarParams {
    /** 0-based месяц, как в Date. */
    month: number;
    year: number;
}

export type CalendarStatus = 'idle' | 'loading' | 'success' | 'error';

export const useCalendarData = ({ month, year }: CalendarParams) => {
    const [data, setData] = useState<CalendarResponse | null>(null);
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

        const monthParam = String(month + 1).padStart(2, '0');
        const path = `/calendar?year=${year}&month=${monthParam}`;

        void getJson<CalendarResponse>(path, { signal: controller.signal })
            .then((result) => {
                if (requestId !== requestIdRef.current) return;
                setData(result ?? {});
                setStatus('success');
            })
            .catch((cause: unknown) => {
                if (requestId !== requestIdRef.current) return;
                // Отмена — не ошибка, просто вышли из состояния загрузки.
                if (cause instanceof DOMException && cause.name === 'AbortError') return;

                setData(null);
                setStatus('error');
                setError(cause instanceof Error ? cause.message : String(cause));
            });

        return () => controller.abort();
    }, [month, year, reloadToken]);

    const reload = useCallback(() => {
        setReloadToken((token) => token + 1);
    }, []);

    return { data, status, error, reload, isLoading: status === 'loading' };
};