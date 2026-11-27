package enerjy.musicxService.Logic.schedule;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Расписание, прошедшее валидацию, — готово к сохранению.
 *
 * <p>Напоминание считает валидатор, а не сценарий сохранения: так правило
 * «когда напоминать» проверяется вместе с правилами приёмки и тестируется
 * изолированно, без базы и транзакций.
 *
 * @param title    короткое название, обрезано по лимиту; может быть null
 * @param note     описание, обрезано по лимиту; может быть null
 * @param startAt  начало как абсолютный момент времени; null для записи «весь день»
 * @param endAt    конец как абсолютный момент времени (из endLocal или startAt + длительность);
 *                 null для записи «весь день»
 * @param zone     зона пользователя, снимок которой надо сохранить вместе с записью
 * @param allDay   запись «весь день»: даты достаточно, конкретного времени нет
 *                 ({@code date = NULL} в БД, {@code startAt}/{@code endAt} пустые)
 * @param remindAt момент напоминания. Для all-day — ровно 00:00 дня события в зоне
 *                 {@code zone}; для обычной записи null — сценарий создаёт T_MINUS_3H
 *                 от {@code startAt}, как раньше.
 */
public record ValidatedSchedule(
        String title,
        String note,
        Instant startAt,
        Instant endAt,
        ZoneId zone,
        boolean allDay,
        Instant remindAt
) {

    /**
     * Старый (до безвременных записей) конструктор: обычная запись с конкретным
     * временем, напоминание считает сценарий. Оставлен, чтобы существующие
     * вызовы и тесты не приходилось переписывать при расширении контракта.
     */
    public ValidatedSchedule(
            String title,
            String note,
            Instant startAt,
            Instant endAt,
            ZoneId zone
    ) {
        this(title, note, startAt, endAt, zone, false, null);
    }
}
