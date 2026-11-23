package enerjy.musicxService.api.dto;

/**
 * Запись календаря на конкретную дату.
 *
 * Форма совпадает с `CalendarEntry` на фронте (src/features/calendar/hooks/useCalendarData.ts).
 * `time` и `title` могут отсутствовать — фронт это переваривает.
 */
public record CalendarEntryDto(
        String date,
        String time,
        String title,
        String note
) {
}