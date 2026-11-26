import {buildUrl, HttpError} from "../api/http";
import {getToken, setToken} from "../api/token";
import {getTelegramWebApp, isTelegramMiniApp} from "./types";

/**
 * Обмен Telegram `initData` на собственный токен бэкенда.
 *
 * `initData` — подписанный блоб, который Telegram инжектит в Mini App при
 * открытии. Бэкенд проверяет его подпись и выдаёт JWT; дальше все запросы
 * идут с `Authorization: Bearer <token>`.
 *
 * Токен живёт в памяти (`api/token.ts`), а не в localStorage: WebView-хранилище
 * на некоторых клиентах чистится, а `initData` приходит при каждом открытии.
 */

export interface TelegramUser {
    id: number;
    firstName: string | null;
    lastName: string | null;
    username: string | null;
}

export interface TelegramAuthResult {
    token: string;
    user: TelegramUser;
}

/** Бэкенд может вернуть и развёрнутый объект, и обёртку data. */
const readAuthResult = (raw: unknown): TelegramAuthResult => {
    if (typeof raw !== 'object' || raw === null) {
        throw new HttpError('auth response is not an object', 0, '/auth/telegram');
    }

    const candidate = raw as Record<string, unknown>;
    const token = typeof candidate.token === 'string' ? candidate.token : null;
    if (!token) {
        throw new HttpError('auth response has no token', 0, '/auth/telegram');
    }

    const rawUser = (candidate.user ?? {}) as Record<string, unknown>;
    const nullableString = (value: unknown): string | null =>
        typeof value === 'string' ? value : null;

    return {
        token,
        user: {
            id: typeof rawUser.id === 'number' ? rawUser.id : Number(rawUser.id ?? 0),
            firstName: nullableString(rawUser.firstName),
            lastName: nullableString(rawUser.lastName),
            username: nullableString(rawUser.username),
        },
    };
};

let cached: TelegramAuthResult | null = null;
let inFlight: Promise<TelegramAuthResult> | null = null;

/**
 * Логинит по `initData`. Идемпотентен: параллельные вызовы (например, старт
 * приложения и первый запрос одновременно) не создадут два логина — все
 * получат один и тот же промис.
 *
 * Бросает, если приложение открыто не в Telegram — там `initData` пустой.
 */
export const loginWithTelegram = async (): Promise<TelegramAuthResult> => {
    if (cached) return cached;

    if (inFlight) return inFlight;

    if (!isTelegramMiniApp()) {
        throw new Error('not a Mini App');
    }

    const initData = getTelegramWebApp()!.initData!;

    inFlight = (async () => {
        const res = await fetch(buildUrl('/auth/telegram'), {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
            body: JSON.stringify({ initData }),
        });

        if (!res.ok) {
            throw new HttpError(`auth failed: ${res.status}`, res.status, '/auth/telegram');
        }

        const parsed = readAuthResult(await res.json());
        cached = parsed;
        setToken(parsed.token);
        // Часовой пояс устройства уходит на бэкенд сразу после входа (шаг 9).
        // Fire-and-forget: ошибка сети или невалидная зона не должны ломать логин.
        void syncTimezone(parsed.token).catch(() => undefined);
        return parsed;
    })();

    try {
        return await inFlight;
    } finally {
        // Сбрасываем только если это всё ещё наш промис: если во время ожидания
        // начался новый логин, обнулять его нельзя.
        if (inFlight) inFlight = null;
    }
};

/**
 * Начинает логин заранее и запоминает промис. `http.ts` ждёт его перед первым
 * запросом, поэтому к моменту вызова `/calendar` токен уже на месте.
 *
 * Вне Telegram ничего не делает и не бросает — приложение должно работать и в
 * обычном браузере (тюнер, метроном), просто без авторизованных запросов.
 */
export const ensureLoggedIn = (): Promise<TelegramAuthResult | null> => {
    if (!isTelegramMiniApp()) return Promise.resolve(null);
    // Не даём unhandled rejection, если никто не успел дождаться.
    return loginWithTelegram().catch(() => null);
};

export const getCachedUser = (): TelegramUser | null => cached?.user ?? null;

/**
 * Отправляет часовой пояс устройства на `PUT /users/me/timezone`.
 *
 * Бэкенд сохраняет её со статусом AUTO и не перетирает ручную зону
 * (команда /timezone, статус MANUAL). Никогда не бросает: ошибка сети или
 * пустая/невалидная зона не ломают логин.
 *
 * @param token    JWT, полученный логином
 * @param timeZone зона; по умолчанию — зона устройства (`Intl`)
 */
export const syncTimezone = async (token: string, timeZone?: string): Promise<boolean> => {
    let zone: string | undefined;
    try {
        zone = timeZone ?? Intl.DateTimeFormat().resolvedOptions().timeZone;
    } catch {
        return false;
    }
    if (!zone) return false;

    try {
        const res = await fetch(buildUrl('/users/me/timezone'), {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                Accept: 'application/json',
                Authorization: `Bearer ${token}`,
            },
            body: JSON.stringify({ timezone: zone }),
        });
        return res.ok;
    } catch {
        return false;
    }
};

export const getTokenFromCache = (): string | null => getToken();

/** Сбрасывает сессию: например, при 401, чтобы следующий запрос логинился заново. */
export const clearSession = (): void => {
    cached = null;
    setToken(null);
};