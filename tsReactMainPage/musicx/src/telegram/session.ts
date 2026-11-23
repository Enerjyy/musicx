import {setUnauthorizedHandler} from "../api/http";
import {setAuthReady} from "../api/token";
import {clearSession, ensureLoggedIn, loginWithTelegram} from "./auth";

/**
 * Собирает аутентификацию воедино: стартовый логин + реакция на 401.
 *
 * Держится отдельно от `App.tsx`, чтобы подключение было в одном месте и не
 * зависело от того, какая страница открыта первой.
 *
 * В обычном браузере (не Mini App) всё превращается в no-op: `ensureLoggedIn`
 * вернёт `null`, токена не будет, и запросы пойдут без `Authorization` —
 * тюнер и метроном продолжат работать.
 */

let installed = false;

export const installSession = (): void => {
    if (installed) return;
    installed = true;

    // Стартовый логин запускаем сразу, а не по факту первого запроса.
    // `http.getJson` всё равно ждёт этот промис, так что гонки нет:
    // запрос не уйдёт раньше, чем появится токен.
    setAuthReady(ensureLoggedIn());

    // 401: токен протух или бэкенд его не знает. Сбрасываем сессию и логинимся
    // заново по initData. Один повтор — если и он не поможет, отдаём ошибку
    // наружу, чтобы UI показал её, а не зациклился.
    setUnauthorizedHandler(async () => {
        clearSession();
        try {
            await loginWithTelegram();
            return true;
        } catch {
            return false;
        }
    });
};