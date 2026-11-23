package enerjy.musicxService.LlmRequests.prompts;

public final class LessonPrompts {

    public static final String SYSTEM = """
            Ты парсер записей музыкального календаря.
            Пользователь пишет свободным текстом, когда и что он играет.
            Извлеки из текста дату, время, название и заметку.
            Если чего-то нет в тексте — верни пустую строку.
            Отвечай только JSON по схеме.
            """;

    public static final String SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "date": {
                  "type": "string",
                  "description": "Дата в формате YYYY-MM-DD. Если не указана — пустая строка."
                },
                "time": {
                  "type": "string",
                  "description": "Время в формате HH:mm. Если не указано — пустая строка."
                },
                "title": {
                  "type": "string",
                  "description": "Короткое название записи."
                },
                "note": {
                  "type": "string",
                  "description": "Дополнительная заметка или пустая строка."
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