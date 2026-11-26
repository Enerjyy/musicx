package enerjy.musicxService.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Запись календаря на конкретную дату.
 *
 * Форма совпадает с `CalendarEntry` на фронте (src/features/calendar/hooks/useCalendarData.ts).
 * `time` и `title` могут отсутствовать — фронт это переваривает.
 *
 * `id` и `endTime` добавлены позже (Шаг 4): фронт игнорирует неизвестные поля,
 * поэтому старый клиент продолжает работать. Пустые поля из JSON убираются
 * (NON_NULL), чтобы не менять ответ для записей без конца/описания.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record CalendarEntryDto(
        Long id,
        String date,
        String time,
        String endTime,
        String title,
        String note
) {
}
