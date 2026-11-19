import { useEffect, useState } from 'react';
import { getTelegramWebApp, isTelegramMiniApp } from './types';

/**
 * Bootstraps the Telegram Mini App: signals that the app is ready, expands it
 * to full height, and asks for fullscreen when the client supports it
 * (Bot API 8.0+). Falls back silently in a regular browser.
 *
 * Returns whether we're running inside Telegram, so the UI can adapt if needed.
 */
export const useTelegram = (): boolean => {
    const [isMiniApp, setIsMiniApp] = useState(false);

    useEffect(() => {
        const tg = getTelegramWebApp();
        if (!tg) return;

        setIsMiniApp(true);

        // 1. Tell Telegram the app is mounted and can be shown.
        tg.ready();

        // 2. Expand to the tallest viewport the client allows.
        tg.expand();

        // 3. Go fullscreen when supported. Some clients reject the call unless
        //    it happens on a user gesture, so we retry once on the first tap.
        const requestFullscreen = () => {
            if (typeof tg.requestFullscreen !== 'function' || tg.isFullscreen) return;
            try {
                tg.requestFullscreen();
            } catch {
                // Older clients / denied gesture — expand() above is enough.
            }
        };

        requestFullscreen();

        const onFirstInteraction = () => {
            requestFullscreen();
            window.removeEventListener('pointerdown', onFirstInteraction);
        };
        window.addEventListener('pointerdown', onFirstInteraction, { once: true });

        // Keep CSS safe-area vars in sync with what the client reports.
        const syncInsets = () => {
            const root = document.documentElement;
            const safe = tg.safeAreaInset;
            const content = tg.contentSafeAreaInset;

            root.style.setProperty('--tg-safe-top', `${safe?.top ?? 0}px`);
            root.style.setProperty('--tg-safe-bottom', `${safe?.bottom ?? 0}px`);
            root.style.setProperty('--tg-safe-left', `${safe?.left ?? 0}px`);
            root.style.setProperty('--tg-safe-right', `${safe?.right ?? 0}px`);

            root.style.setProperty('--tg-content-top', `${content?.top ?? 0}px`);
            root.style.setProperty('--tg-content-bottom', `${content?.bottom ?? 0}px`);

            // In fullscreen the layout viewport can disagree with dvh, so mirror
            // the height Telegram reports and let CSS use it when present.
            if (tg.viewportHeight && tg.viewportHeight > 0) {
                root.style.setProperty('--tg-viewport-height', `${tg.viewportHeight}px`);
            }
        };

        syncInsets();

        tg.onEvent('viewportChanged', syncInsets);
        tg.onEvent('fullscreenChanged', syncInsets);

        return () => {
            tg.offEvent('viewportChanged', syncInsets);
            tg.offEvent('fullscreenChanged', syncInsets);
            window.removeEventListener('pointerdown', onFirstInteraction);
        };
    }, []);

    return isMiniApp;
};

export { isTelegramMiniApp } from './types';