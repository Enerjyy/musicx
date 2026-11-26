import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * Тесты шага 9: после успешного входа Mini App отправляет часовой пояс
 * устройства на PUT /users/me/timezone с auth-токеном. Ошибки сети и
 * пустая зона логин не ломают.
 */

const webApp = { initData: 'init-data' };

vi.mock('./types', () => ({
    isTelegramMiniApp: () => true,
    getTelegramWebApp: () => webApp,
}));

import { clearSession, loginWithTelegram, syncTimezone } from './auth';

const fetchMock = vi.fn();

const stubResponse = (body: unknown, ok = true) => ({
    ok,
    json: async () => body,
});

const authResult = {
    token: 'jwt-token',
    user: { id: 1, firstName: 'Ada', lastName: null, username: 'ada' },
};

beforeEach(() => {
    clearSession();
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
});

describe('syncTimezone', () => {
    it('отправляет PUT с токеном и переданной зоной', async () => {
        fetchMock.mockResolvedValue(stubResponse({ timezone: 'Europe/Berlin', status: 'AUTO' }));

        const ok = await syncTimezone('jwt-token', 'Europe/Berlin');

        expect(ok).toBe(true);
        expect(fetchMock).toHaveBeenCalledTimes(1);
        const [url, init] = fetchMock.mock.calls[0];
        expect(String(url)).toContain('/users/me/timezone');
        expect(init.method).toBe('PUT');
        expect(init.headers.Authorization).toBe('Bearer jwt-token');
        expect(JSON.parse(init.body)).toEqual({ timezone: 'Europe/Berlin' });
    });

    it('ошибка сети не бросает и возвращает false', async () => {
        fetchMock.mockRejectedValue(new Error('network down'));

        await expect(syncTimezone('jwt-token', 'Europe/Berlin')).resolves.toBe(false);
    });

    it('пустая зона не отправляет запрос', async () => {
        await expect(syncTimezone('jwt-token', '')).resolves.toBe(false);
        expect(fetchMock).not.toHaveBeenCalled();
    });
});

describe('loginWithTelegram', () => {
    it('после успешного входа отправляет зону устройства с токеном', async () => {
        fetchMock
            .mockResolvedValueOnce(stubResponse(authResult))
            .mockResolvedValueOnce(stubResponse({ timezone: 'Europe/London', status: 'AUTO' }));

        const result = await loginWithTelegram();
        expect(result.token).toBe('jwt-token');

        // Синк зоны уходит fire-and-forget: даём микротаску отработать.
        await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));

        const [, init] = fetchMock.mock.calls[1];
        expect(init.method).toBe('PUT');
        expect(init.headers.Authorization).toBe('Bearer jwt-token');
        expect(JSON.parse(init.body).timezone).toBeTruthy();
    });

    it('сбой синка зоны не ломает логин', async () => {
        fetchMock
            .mockResolvedValueOnce(stubResponse(authResult))
            .mockRejectedValue(new Error('network down'));

        const result = await loginWithTelegram();

        expect(result.token).toBe('jwt-token');
        await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    });
});
