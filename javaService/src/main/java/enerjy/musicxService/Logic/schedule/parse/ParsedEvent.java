package enerjy.musicxService.Logic.schedule.parse;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;

/**
 * Результат разбора свободного текста пользователя в событие календаря.
 *
 * Модель возвращает только локальные значения пользователя ({@code startLocal},
 * {@code endLocal}), зона здесь не хранится: {@code Instant} вычисляет код
 * через {@link #startAt(ZoneId)}, где переходы на летнее время обрабатывает
 * {@link LocalDateTime#atZone(ZoneId)}.
 *
 * @param isEvent         сообщение вообще является попыткой создать запись
 * @param title           короткое название, может отсутствовать
 * @param description     дополнительное описание, может отсутствовать
 * @param startLocal      начало в локальном времени пользователя, null если не найдено
 * @param endLocal        конец, если пользователь указал только конец («до 16»)
 * @param durationMinutes длительность в минутах, если указана («на 2 часа»)
 * @param missing         чего не хватает: "date" и/или "time"
 * @param ambiguous       время неоднозначно (например «в 5» без контекста)
 */
public record ParsedEvent(
        boolean isEvent,
        String title,
        String description,
        LocalDateTime startLocal,
        LocalDateTime endLocal,
        Integer durationMinutes,
        Set<String> missing,
        boolean ambiguous
) {

    public ParsedEvent {
        missing = missing == null ? Set.of() : Set.copyOf(missing);
    }

    /** Начало как абсолютный момент времени в зоне пользователя. */
    public Instant startAt(ZoneId zone) {
        return at(zone, startLocal, "startLocal");
    }

    /** Конец как абсолютный момент времени в зоне пользователя. */
    public Instant endAt(ZoneId zone) {
        return at(zone, endLocal, "endLocal");
    }

    private static Instant at(ZoneId zone, LocalDateTime value, String field) {
        Objects.requireNonNull(zone, "zone");
        if (value == null) {
            throw new IllegalStateException(field + " is absent");
        }
        // Правила для DST задаёт atZone (= ZonedDateTime.ofLocal без выбранного оффсета):
        // неоднозначное локальное время при переводе стрелок назад даёт первое (раннее)
        // вхождение, несуществующее время при переводе вперёд сдвигается на длину перехода.
        return value.atZone(zone).toInstant();
    }
}
