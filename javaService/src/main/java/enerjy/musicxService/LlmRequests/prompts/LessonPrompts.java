package enerjy.musicxService.LlmRequests.prompts;

public final class LessonPrompts {

    public static final String SYSTEM = """
            Ты парсер записей музыкального календаря.
            Пользователь пишет свободным текстом, когда и что он играет.
            Извлеки дату, время, название и заметку.
            Дата и время обязательны. Если их нет в тексте — верни пустые строки,
            не пытайся угадать или подставить текущую дату.
            Отвечай только JSON по схеме.
            """;

    public static final String SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "date": {
                  "type": "string",
                  "description": "Дата в формате YYYY-MM-DD. Если в тексте нет даты — пустая строка."
                },
                "time": {
                  "type": "string",
                  "description": "Время в формате HH:mm, 24 часа. Если в тексте нет времени — пустая строка."
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