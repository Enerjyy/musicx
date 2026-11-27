import { describe, expect, it } from 'vitest';

/**
 * Тесты группировки ответа календаря (этап 7 плана):
 * новые поля (id/endTime/allDay) доходят до UI, безвременные записи
 * сортируются в конец дня, битые записи пропускаются.
 */
import { groupEntries, type CalendarResponse } from './useCalendarData';

describe('groupEntries', () => {
    it('сохраняет id, endTime и allDay у записей', () => {
        const response: CalendarResponse = [
            { id: 7, date: '2026-03-05', time: '15:00:00', endTime: '16:30:00', title: 'Урок' },
            { id: 9, date: '2026-03-06', allDay: true, title: 'Поездка' },
        ];

        const byDate = groupEntries(response);

        expect(byDate.get('2026-03-05')).toEqual([
            {
                id: 7,
                date: '2026-03-05',
                time: '15:00',
                endTime: '16:30',
                allDay: undefined,
                title: 'Урок',
                note: undefined,
            },
        ]);
        expect(byDate.get('2026-03-06')).toEqual([
            {
                id: 9,
                date: '2026-03-06',
                time: undefined,
                endTime: undefined,
                allDay: true,
                title: 'Поездка',
                note: undefined,
            },
        ]);
    });

    it('нормализует endTime так же, как time (HH:mm:ss → HH:mm)', () => {
        const byDate = groupEntries([
            { id: 1, date: '2026-03-05', time: '09:00', endTime: '10:00:00' },
        ]);

        expect(byDate.get('2026-03-05')?.[0]).toMatchObject({ time: '09:00', endTime: '10:00' });
    });

    it('битые endTime и id отбрасывает, запись остаётся валидной', () => {
        const byDate = groupEntries([
            { date: '2026-03-05', time: '12:00', endTime: 42, id: 'abc' },
        ]);

        expect(byDate.get('2026-03-05')?.[0]).toEqual({
            date: '2026-03-05',
            time: '12:00',
            endTime: undefined,
            id: undefined,
            allDay: undefined,
            title: undefined,
            note: undefined,
        });
    });

    it('записи без времени (allDay) уходят в конец дня при сортировке', () => {
        const byDate = groupEntries([
            { id: 3, date: '2026-03-05', allDay: true, title: 'Весь день' },
            { id: 1, date: '2026-03-05', time: '18:00', title: 'Вечер' },
            { id: 2, date: '2026-03-05', time: '09:00', title: 'Утро' },
        ]);

        expect(byDate.get('2026-03-05')?.map((entry) => entry.title)).toEqual([
            'Утро',
            'Вечер',
            'Весь день',
        ]);
    });

    it('группирует по дате, отбрасывая время из даты и битые записи', () => {
        const byDate = groupEntries([
            { id: 1, date: '2026-03-05T15:00:00+03:00', title: 'С офсетом' },
            'мусор',
            null,
            { time: '10:00' }, // нет даты — пропуск
        ]);

        expect([...byDate.keys()]).toEqual(['2026-03-05']);
        expect(byDate.get('2026-03-05')).toHaveLength(1);
    });

    it('пустой и невалидный ответ даёт пустую карту', () => {
        expect(groupEntries(null).size).toBe(0);
        expect(groupEntries({}).size).toBe(0);
        expect(groupEntries([]).size).toBe(0);
    });
});
