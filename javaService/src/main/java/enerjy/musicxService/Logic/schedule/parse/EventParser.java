package enerjy.musicxService.Logic.schedule.parse;

import java.time.ZoneId;

/** Разбор свободного текста пользователя в событие календаря. */
public interface EventParser {

    /**
     * @param userText свободный текст пользователя
     * @param userZone часовой пояс пользователя: относительные даты
     *                 («завтра», «в пятницу») считаются от него
     */
    ParsedEvent parse(String userText, ZoneId userZone);
}
