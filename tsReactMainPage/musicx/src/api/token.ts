/**
 * Токен текущей сессии.
 *
 * Вынесен из auth.ts, потому что http.ts и auth.ts нужны друг другу:
 * http подставляет токен в заголовок и обрабатывает 401, auth его выдаёт.
 * Общий модуль без зависимостей разрывает цикл — оба импортируют только его.
 */

let token: string | null = null;

/**
 * Разрешается, когда стартовый логин завершится (успешно или нет).
 * http.ts ждёт этот промис перед первым запросом, иначе он ушёл бы без
 * токена, получил 401 и его пришлось бы переигрывать.
 */
let ready: Promise<unknown> = Promise.resolve();

export const getToken = (): string | null => token;

export const setToken = (value: string | null): void => {
    token = value;
};

export const getAuthReady = (): Promise<unknown> => ready;

export const setAuthReady = (promise: Promise<unknown>): void => {
    ready = promise;
};