/**
 * Тонкий слой над fetch: базовый URL + разбор ошибок.
 *
 * Адрес берётся из `VITE_API_URL` (build-arg в Dockerfile, уже прокинут из
 * docker-compose). В dev переменной обычно нет, поэтому подставляем локальный
 * бэкенд — `server.port: 8081` из javaService/application.yml. Если нужен
 * другой адрес в dev, достаточно создать `.env.local` с
 * `VITE_API_URL=...` (файл в .gitignore).
 */

const DEV_FALLBACK = 'http://localhost:8081';

const rawBase = import.meta.env.VITE_API_URL as string | undefined;
const trimmed = rawBase?.trim();

export const API_BASE_URL = (trimmed ? trimmed.replace(/\/+$/, '') : DEV_FALLBACK);

if (import.meta.env.DEV && !trimmed) {
    console.info(`[api] VITE_API_URL не задан, использую ${API_BASE_URL}`);
}

/** Ошибка запроса: сеть, не-2xx или битый JSON — всё сюда. */
export class HttpError extends Error {
    readonly status: number;
    readonly url: string;

    constructor(message: string, status: number, url: string) {
        super(message);
        this.name = 'HttpError';
        this.status = status;
        this.url = url;
    }
}

export const buildUrl = (path: string): string => {
    const suffix = path.startsWith('/') ? path : `/${path}`;
    return `${API_BASE_URL}${suffix}`;
};

interface GetJsonOptions {
    signal?: AbortSignal;
}

/**
 * GET с разбором JSON. 204/205/304 и пустое тело дают `undefined`,
 * не-2xx бросает HttpError.
 */
export const getJson = async <T>(
    path: string,
    options: GetJsonOptions = {},
): Promise<T> => {
    const url = buildUrl(path);

    let response: Response;
    try {
        response = await fetch(url, {
            method: 'GET',
            headers: { Accept: 'application/json' },
            signal: options.signal,
        });
    } catch (error) {
        // Abort — это штатная отмена (уход со страницы), не ошибка.
        if (error instanceof DOMException && error.name === 'AbortError') throw error;
        throw new HttpError(
            error instanceof Error ? `Network error: ${error.message}` : 'Network error',
            0,
            url,
        );
    }

    if (!response.ok) {
        throw new HttpError(`HTTP ${response.status} ${response.statusText}`.trim(), response.status, url);
    }

    if (response.status === 204 || response.status === 205 || response.status === 304) {
        return undefined as T;
    }

    const text = await response.text();
    if (!text) return undefined as T;

    try {
        return JSON.parse(text) as T;
    } catch {
        throw new HttpError('Response is not valid JSON', response.status, url);
    }
};