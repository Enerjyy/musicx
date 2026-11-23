/**
 * Тонкий слой над fetch: базовый URL, токен авторизации и разбор ошибок.
 *
 * Адрес берётся из `VITE_API_URL` (build-arg в Dockerfile, уже прокинут из
 * docker-compose). В dev переменной обычно нет, поэтому подставляем локальный
 * бэкенд — `server.port: 8081` из javaService/application.yml. Если нужен
 * другой адрес в dev, достаточно создать `.env.local` с
 * `VITE_API_URL=...` (файл в .gitignore).
 *
 * Токен берётся из `api/token.ts` — отдельного модуля без зависимостей, чтобы
 * не было цикла: этот файл не импортирует auth.ts, а получает токен и функцию
 * перелогина через параметры.
 */
import { getAuthReady, getToken } from "./token";

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

interface RequestOptions {
    signal?: AbortSignal;
    /** Пропустить заголовок Authorization (для самого /auth/telegram). */
    skipAuth?: boolean;
    headers?: Record<string, string>;
}

const isAbort = (error: unknown): boolean =>
    error instanceof DOMException && error.name === 'AbortError';

/**
 * Вызывается при 401, чтобы дать шанс обновить токен и повторить запрос.
 * Ставится извне (`setUnauthorizedHandler`), чтобы не тянуть auth.ts сюда.
 */
type UnauthorizedHandler = () => Promise<boolean>;

let unauthorizedHandler: UnauthorizedHandler | null = null;

export const setUnauthorizedHandler = (handler: UnauthorizedHandler | null): void => {
    unauthorizedHandler = handler;
};

const requestOnce = async <T>(
    path: string,
    init: RequestInit,
    url: string,
): Promise<T> => {
    let response: Response;
    try {
        response = await fetch(url, init);
    } catch (error) {
        // Abort — это штатная отмена (уход со страницы), не ошибка.
        if (isAbort(error)) throw error;
        throw new HttpError(
            error instanceof Error ? `Network error: ${error.message}` : 'Network error',
            0,
            url,
        );
    }

    if (!response.ok) {
        throw new HttpError(
            `HTTP ${response.status} ${response.statusText}`.trim(),
            response.status,
            url,
        );
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

const buildHeaders = (options: RequestOptions): Record<string, string> => {
    const headers: Record<string, string> = { Accept: 'application/json' };

    if (!options.skipAuth) {
        const token = getToken();
        if (token) headers.Authorization = `Bearer ${token}`;
    }

    return { ...headers, ...options.headers };
};

/**
 * GET с разбором JSON. 204/205/304 и пустое тело дают `undefined`.
 *
 * Если бэкенд ответил 401 и зарегистрирован обработчик — один раз пробуем
 * перелогиниться и повторяем запрос. Больше одного повтора не делаем, чтобы
 * не зациклиться на неправильных креденшлах.
 */
export const getJson = async <T>(
    path: string,
    options: RequestOptions = {},
): Promise<T> => {
    const url = buildUrl(path);

    // Ждём стартовый логин, иначе первый запрос уйдёт без токена.
    await getAuthReady();

    const init: RequestInit = {
        method: 'GET',
        headers: buildHeaders(options),
        signal: options.signal,
    };

    try {
        return await requestOnce<T>(path, init, url);
    } catch (error) {
        if (!(error instanceof HttpError) || error.status !== 401) throw error;
        if (options.skipAuth || !unauthorizedHandler) throw error;

        const retried = await unauthorizedHandler();
        if (!retried) throw error;

        // Повторяем с обновлённым токеном.
        const retryInit: RequestInit = {
            ...init,
            headers: buildHeaders(options),
        };
        return await requestOnce<T>(path, retryInit, url);
    }
};

/** POST с JSON-телом. Тот же разбор ответа и обработка 401, что у getJson. */
export const postJson = async <T, B = unknown>(
    path: string,
    body: B,
    options: RequestOptions = {},
): Promise<T> => {
    const url = buildUrl(path);

    await getAuthReady();

    const init: RequestInit = {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...buildHeaders(options) },
        body: JSON.stringify(body),
        signal: options.signal,
    };

    try {
        return await requestOnce<T>(path, init, url);
    } catch (error) {
        if (!(error instanceof HttpError) || error.status !== 401) throw error;
        if (options.skipAuth || !unauthorizedHandler) throw error;

        const retried = await unauthorizedHandler();
        if (!retried) throw error;

        const retryInit: RequestInit = {
            ...init,
            headers: { 'Content-Type': 'application/json', ...buildHeaders(options) },
        };
        return await requestOnce<T>(path, retryInit, url);
    }
};