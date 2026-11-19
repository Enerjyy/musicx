import { useEffect, useState } from 'react';
import { getTelegramWebApp } from './types';

/**
 * Height Telegram's floating header occupies in fullscreen, used as a fallback
 * until the client reports the real contentSafeAreaInset. Without this the
 * header renders underneath Telegram's close/minimise pill and only settles
 * once the inset arrives.
 */
const FALLBACK_HEADER_TOP = 46;

/**
 * Bootstraps the Telegram Mini App: signals readiness, expands to full height,
 * and requests fullscreen where supported (Bot API 8.0+). Silent no-op in a
 * regular browser.
 *
 * Returns whether we're running inside Telegram, so the UI can adapt if needed.
 */
export const useTelegram = (): boolean => {
    const [isMiniApp, setIsMiniApp] = useState(false);

    useEffect(() => {
        const tg = getTelegramWebApp();
        if (!tg) return;

        // В обычном браузере объект Telegram.WebApp тоже существует (скрипт
        // telegram.org всегда его создаёт), но platform === "unknown" и initData
        // пустой. Без этой проверки приложение считало себя Mini App и звало
        // requestFullscreen, которого нет в старых версиях — отсюда ошибки
        // в консоли "[Telegram.WebApp] Method requestFullscreen is not supported".
        const inTelegram = tg.platform !== 'unknown' && Boolean(tg.initData);
        if (!inTelegram) return;

        setIsMiniApp(true);

        tg.ready();
        tg.expand();

        const requestFullscreen = () => {
            if (typeof tg.requestFullscreen !== 'function' || tg.isFullscreen) return;
            try {
                tg.requestFullscreen();
            } catch {
                // Старые клиенты или жест не принят — expand() выше достаточно.
            }
        };

        requestFullscreen();

        // Some clients only honour the request after the webview is fully active,
        // and others require a user gesture. Retry on activation and on the first
        // tap; both are cheap and idempotent thanks to the isFullscreen guard.
        const onActivated = () => requestFullscreen();
        const onFirstPointer = () => {
            requestFullscreen();
            window.removeEventListener('pointerdown', onFirstPointer);
        };

        tg.onEvent('activated', onActivated);
        window.addEventListener('pointerdown', onFirstPointer, { once: true });

        const syncInsets = () => {
            const root = document.documentElement;
            const safe = tg.safeAreaInset;
            const content = tg.contentSafeAreaInset;

            // In fullscreen Telegram overlays its own header; contentSafeAreaInset
            // carries that height. safeAreaInset only covers the device notch.
            // The fallback prevents a flash of the header sitting under the pill.
            const top = Math.max(
                safe?.top ?? 0,
                content?.top ?? 0,
                tg.isFullscreen ? FALLBACK_HEADER_TOP : 0,
            );
            const bottom = Math.max(safe?.bottom ?? 0, content?.bottom ?? 0);

            root.style.setProperty('--tg-inset-top', `${top}px`);
            root.style.setProperty('--tg-inset-bottom', `${bottom}px`);
            root.style.setProperty('--tg-safe-left', `${safe?.left ?? 0}px`);
            root.style.setProperty('--tg-safe-right', `${safe?.right ?? 0}px`);

            // Fullscreen can disagree with dvh, so mirror the reported height.
            if (tg.viewportHeight && tg.viewportHeight > 0) {
                root.style.setProperty('--tg-viewport-height', `${tg.viewportHeight}px`);
            }
        };

        syncInsets();

        tg.onEvent('viewportChanged', syncInsets);
        tg.onEvent('fullscreenChanged', syncInsets);
        tg.onEvent('activated', syncInsets);

        return () => {
            tg.offEvent('viewportChanged', syncInsets);
            tg.offEvent('fullscreenChanged', syncInsets);
            tg.offEvent('activated', syncInsets);
            window.removeEventListener('pointerdown', onFirstPointer);
        };
    }, []);

    return isMiniApp;
};