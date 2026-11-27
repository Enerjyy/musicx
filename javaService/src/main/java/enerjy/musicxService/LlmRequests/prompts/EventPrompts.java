package enerjy.musicxService.LlmRequests.prompts;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Промпт и JSON-схема контракта v2 парсинга событий (Шаг 3 + интенты).
 *
 * Модель видит локальные дату, время, день недели и зону пользователя и
 * возвращает только локальные значения ({@code YYYY-MM-DDTHH:mm}, без
 * смещений и без Z): {@code Instant} из них считает Java-код, поэтому
 * часовые пояса и переходы на летнее время не зависят от модели.
 *
 * <p>Отличия от v1: добавлены {@code intent} (создать/перенести/удалить),
 * блок {@code match} (опорные точки существующей записи для update/delete)
 * и {@code allDay} («весь день» — дата без времени). Java-код толерантен к
 * ответам без этих полей: нет intent — считается CREATE.
 */
public final class EventPrompts {

    private static final String SYSTEM_TEMPLATE = """
            Ты парсер событий музыкального календаря.
            Сейчас у пользователя %s, %s, местное время %s, часовой пояс %s.
            Пользователь пишет свободным текстом о записях в календаре.
            Определи intent:
            - "create" — пользователь хочет создать новую запись (по умолчанию);
            - "update" — перенести или исправить существующую запись («перенеси», «поставь на другое время», «давай в пятницу вместо среды»);
            - "delete" — удалить существующую запись («удали», «отмени»).
            Для update/delete заполни match — опорные точки СУЩЕСТВУЮЩЕЙ записи, о которой речь:
            - match.date — дата существующей записи (YYYY-MM-DD), если понятна;
            - match.time — время существующей записи (HH:mm), если понятно;
            - match.title — название (или слова, по которым запись можно узнать), если понятно.
            При update новое время и новая дата записываются как обычно в startLocal
            (или endLocal/durationMinutes, если пользователь уточнил только их).
            При delete новые время и дату не выдумывай: startLocal, endLocal, durationMinutes оставь пустыми.
            Извлеки:
            - title — короткое название;
            - description — дополнительное описание;
            - startLocal — начало в формате YYYY-MM-DDTHH:mm;
            - endLocal — конец, если пользователь указал только конец («до 16:00»);
            - durationMinutes — длительность в минутах, если она указана («на 2 часа», «на 45 минут»);
            - allDay — true, если пользователь назначил событие на весь день без конкретного
              времени («весь день», «целый день», «на весь день»): тогда startLocal = дата
              события и время 00:00 (например "2026-11-28T00:00"), а в missing добавь "time";
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
            Примеры интентов: "перенеси урок гитары на пятницу 18:00" → intent "update",
            match.title "урок гитары", startLocal с датой ближайшей пятницы и временем 18:00;
            "удали запись про гитару в среду" → intent "delete", match.date со средой,
            match.title "гитару"; "завтра репетиция, весь день" → intent "create",
            allDay true, startLocal с датой завтрашнего дня и временем 00:00, missing ["time"].
            Если сообщение — не про записи календаря (команда, приветствие, вопрос) — верни
            isEvent=false и пустые поля.
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
                  "description": "true, если сообщение про запись в календаре (создать, перенести или удалить)."
                },
                "intent": {
                  "type": ["string", "null"],
                  "enum": ["create", "update", "delete", null],
                  "description": "create — создать запись, update — перенести/исправить существующую, delete — удалить существующую. Null допустим и означает create."
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
                  "description": "Начало строго в формате YYYY-MM-DDTHH:mm, локальное время пользователя, без смещения и без Z. Для all-day события — дата и время 00:00, для delete — null."
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
                "allDay": {
                  "type": "boolean",
                  "description": "true, если событие на весь день без конкретного времени («весь день»); тогда startLocal — дата и время 00:00, а в missing есть time."
                },
                "match": {
                  "type": ["object", "null"],
                  "properties": {
                    "date": {
                      "type": ["string", "null"],
                      "description": "Дата существующей записи для update/delete, YYYY-MM-DD. Если нет — null."
                    },
                    "time": {
                      "type": ["string", "null"],
                      "description": "Время существующей записи для update/delete, HH:mm. Если нет — null."
                    },
                    "title": {
                      "type": ["string", "null"],
                      "maxLength": 200,
                      "description": "Название (или его часть) существующей записи для update/delete. Если нет — null."
                    }
                  },
                  "required": ["date", "time", "title"],
                  "additionalProperties": false,
                  "description": "Опорные точки существующей записи. Только для update/delete, иначе null."
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
              "required": ["isEvent", "intent", "title", "description", "startLocal", "endLocal",
                           "durationMinutes", "allDay", "match", "missing", "ambiguous"],
              "additionalProperties": false
            }
            """;

    public static final String SCHEMA_NAME = "calendar_event";

    private EventPrompts() {
    }
}
