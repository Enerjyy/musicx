package enerjy.musicxService.LlmRequests.prompts;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Промпт и JSON-схема контракта v1 парсинга событий (Шаг 3).
 *
 * Модель видит локальные дату, время, день недели и зону пользователя и
 * возвращает только локальные значения ({@code YYYY-MM-DDTHH:mm}, без
 * смещений и без Z): {@code Instant} из них считает Java-код, поэтому
 * часовые пояса и переходы на летнее время не зависят от модели.
 */
public final class EventPrompts {

    private static final String SYSTEM_TEMPLATE = """
            Ты парсер событий музыкального календаря.
            Сейчас у пользователя %s, %s, местное время %s, часовой пояс %s.
            Пользователь пишет свободным текстом, когда и что он играет, и хочет создать запись.
            Если сообщение — не попытка создать запись (команда, приветствие, вопрос) — верни
            isEvent=false и пустые поля.
            Извлеки:
            - title — короткое название;
            - description — дополнительное описание;
            - startLocal — начало в формате YYYY-MM-DDTHH:mm;
            - endLocal — конец, если пользователь указал только конец («до 16:00»);
            - durationMinutes — длительность в минутах, если она указана («на 2 часа», «на 45 минут»);
            - missing — чего не хватает: ["date"], ["time"] или ["date","time"];
            - ambiguous — true, если время неоднозначно без контекста (например «в 5» —
              неясно, утра или вечера, и неизвестна дата).
            Относительные даты («сегодня», «завтра», «послезавтра», «в следующую среду») вычисли
            от текущей даты пользователя.
            Возвращай только локальные дату и время пользователя, строго YYYY-MM-DDTHH:mm,
            без смещений, без Z и без упоминания зоны: часовой пояс системе уже известен.
            Если даты нет в тексте и её нельзя вычислить из текущей даты — startLocal пустой,
            а в missing добавь "date". Дату и время не выдумывай.
            Названия месяцев на любом языке заменяй на номер месяца.
            Примеры: "25 ноября 2026 года 15:00" → "2026-11-25T15:00",
            "November 25, 2026 3:00 PM" → "2026-11-25T15:00",
            "с 14 до 16" → startLocal с временем 14:00 и endLocal с временем 16:00.
            Отвечай только JSON по схеме.
            """;

    public static String system(ZonedDateTime userNow) {
        return SYSTEM_TEMPLATE.formatted(
                userNow.toLocalDate(),
                userNow.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.of("ru")),
                userNow.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                userNow.getZone().getId());
    }

    public static final String SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "isEvent": {
                  "type": "boolean",
                  "description": "true, если пользователь хочет создать запись в календаре."
                },
                "title": {
                  "type": ["string", "null"],
                  "maxLength": 200,
                  "description": "Короткое название события. Если нет — null."
                },
                "description": {
                  "type": ["string", "null"],
                  "maxLength": 1000,
                  "description": "Дополнительное описание. Если нет — null."
                },
                "startLocal": {
                  "type": ["string", "null"],
                  "description": "Начало строго в формате YYYY-MM-DDTHH:mm, локальное время пользователя, без смещения и без Z. Если нет — null."
                },
                "endLocal": {
                  "type": ["string", "null"],
                  "description": "Конец строго в формате YYYY-MM-DDTHH:mm, если пользователь указал только конец. Иначе null."
                },
                "durationMinutes": {
                  "type": ["integer", "null"],
                  "minimum": 1,
                  "description": "Длительность в минутах, если указана. Иначе null."
                },
                "missing": {
                  "type": "array",
                  "items": { "type": "string", "enum": ["date", "time"] },
                  "description": "Чего не хватает в тексте и что нельзя вычислить."
                },
                "ambiguous": {
                  "type": "boolean",
                  "description": "true, если время неоднозначно без контекста, например «в 5»."
                }
              },
              "required": ["isEvent", "title", "description", "startLocal", "endLocal",
                           "durationMinutes", "missing", "ambiguous"],
              "additionalProperties": false
            }
            """;

    public static final String SCHEMA_NAME = "calendar_event";

    private EventPrompts() {
    }
}
