package enerjy.musicxService.Logic.schedule;

import enerjy.musicxService.Logic.schedule.parse.ParsedEvent;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Расписание, прошедшее валидацию, — готово к сохранению.
 *
 * @param title   короткое название, обрезано по лимиту; может быть null
 * @param note    описание, обрезано по лимиту; может быть null
 * @param startAt начало как абсолютный момент времени
 * @param endAt   конец как абсолютный момент времени (из endLocal или startAt + длительность)
 * @param zone    зона пользователя, снимок которой надо сохранить вместе с записью
 */
public record ValidatedSchedule(
        String title,
        String note,
        Instant startAt,
        Instant endAt,
        ZoneId zone
) {
}
