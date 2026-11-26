package enerjy.musicxService.LlmRequests.prompts;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

public final class LessonPrompts {

    private static final String SYSTEM_TEMPLATE = """
            Ты парсер записей музыкального календаря.
            Сегодня %s, %s. Пользователь может говорить «сегодня», «завтра», «послезавтра»,
            «в следующую среду» и другие относительные даты — вычисли конкретную дату
            относительно сегодняшней.
            Пользователь пишет свободным текстом, когда и что он играет.
            Извлеки дату, время, название и заметку.
            Дата и время обязательны. Если даты нет в тексте и её нельзя вычислить
            из сегодняшней даты — верни пустые строки, не выдумывай дату.
            Дату всегда приводи к YYYY-MM-DD, время — к HH:mm по 24-часовой шкале.
            Названия месяцев на любом языке заменяй на номер месяца.
            Примеры: "25 ноября 2026 года" → "2026-11-25",
            "November 25, 2026" → "2026-11-25", "3:00 PM" → "15:00".
            Отвечай только JSON по схеме.
            """;

    public static String system(LocalDate today) {
        return SYSTEM_TEMPLATE.formatted(
                today,
                today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.of("ru")));
    }

    public static final String SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "date": {
                  "type": "string",
                  "description": "Дата строго в формате YYYY-MM-DD. Если в тексте нет даты — пустая строка."
                },
                "time": {
                  "type": "string",
                  "description": "Время строго в формате HH:mm по 24 часам. Если в тексте нет времени — пустая строка."
                },
                "title": {
                  "type": "string",
                  "maxLength": 200,
                  "description": "Короткое название записи. Если нет — пустая строка."
                },
                "note": {
                  "type": "string",
                  "maxLength": 255,
                  "description": "Дополнительная заметка. Если нет — пустая строка."
                }
              },
              "required": ["date", "time", "title", "note"],
              "additionalProperties": false
            }
            """;

    public static final String SCHEMA_NAME = "lesson";

    private LessonPrompts() {
    }
}
