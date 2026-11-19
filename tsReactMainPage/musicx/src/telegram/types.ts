/**
 * Minimal typings for the parts of the Telegram WebApp API we use.
 * Only the surface we actually touch is declared, so we don't have to depend
 * on @twa-dev/types or the official @telegram-apps/sdk.
 */
export interface TelegramWebApp {
    /** Always call once the app is mounted and ready to be shown. */
    ready: () => void;
    /** Expands the app to the maximum available height on open. */
    expand: () => void;
    /** Bot API 8.0+. Absent on older clients and in the web browser. */
    requestFullscreen?: () => void;
    /** Bot API 8.0+. */
    exitFullscreen?: () => void;
    isFullscreen?: boolean;
    viewportHeight?: number;
    viewportStableHeight?: number;
    safeAreaInset?: { top: number; bottom: number; left: number; right: number };
    contentSafeAreaInset?: { top: number; bottom: number; left: number; right: number };
    colorScheme?: 'light' | 'dark';
    themeParams?: Record<string, string | undefined>;
    initData?: string;
    initDataUnsafe?: { user?: { id: number; first_name?: string; last_name?: string } };
    onEvent: (event: TelegramEvent, handler: () => void) => void;
    offEvent: (event: TelegramEvent, handler: () => void) => void;
}

/** Only the lifecycle events we actually subscribe to. */
export type TelegramEvent =
    | 'activated'
    | 'viewportChanged'
    | 'fullscreenChanged'
    | 'fullscreenFailed'
    | 'themeChanged';

declare global {
    interface Window {
        Telegram?: { WebApp?: TelegramWebApp };
    }
}

export const getTelegramWebApp = (): TelegramWebApp | undefined =>
    typeof window === 'undefined' ? undefined : window.Telegram?.WebApp;

export const isTelegramMiniApp = (): boolean => Boolean(getTelegramWebApp()?.initData);