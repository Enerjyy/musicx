import * as Tone from 'tone';

/**
 * Browsers — and the Telegram WebView on iOS in particular — keep the
 * AudioContext "suspended" until it is created or resumed from inside a real
 * user gesture. Tone creates its context at import time, i.e. outside any
 * gesture, so the first interaction anywhere in the app has to unlock it.
 * Without this, nothing is audible until some other code path happens to call
 * Tone.start() (previously: enabling the microphone).
 *
 * Audio is unlocked on the first gesture of any kind and the result memoised,
 * so every caller can safely await it.
 */

let unlockPromise: Promise<void> | null = null;

const unlock = async (): Promise<void> => {
    try {
        if (Tone.getContext().state !== 'running') {
            await Tone.start();
        }

        // Some WebViews resolve start() while still reporting "suspended".
        const context = Tone.getContext();
        if (context.state !== 'running' && typeof context.resume === 'function') {
            await context.resume();
        }
    } catch (error) {
        console.error('Не удалось разблокировать аудио:', error);
    }
};

/** Kick off (or await) the audio unlock. Safe to call on every interaction. */
export const unlockAudio = (): Promise<void> => {
    if (Tone.getContext().state === 'running') return Promise.resolve();
    if (!unlockPromise) unlockPromise = unlock();
    return unlockPromise;
};

/**
 * Registers one-shot listeners that unlock audio on the first gesture, and
 * clears the memo if that attempt failed so a later gesture can retry.
 * Call once at app start; returns a cleanup function.
 */
export const installAudioUnlock = (): (() => void) => {
    const events = ['pointerdown', 'mousedown', 'touchstart', 'keydown'] as const;

    const handler = () => {
        void unlockAudio().then(() => {
            if (Tone.getContext().state !== 'running') {
                // Failed — allow a later gesture to try again.
                unlockPromise = null;
            }
        });
    };

    for (const event of events) {
        window.addEventListener(event, handler, { capture: true, passive: true });
    }

    return () => {
        for (const event of events) {
            window.removeEventListener(event, handler, { capture: true });
        }
    };
};