package enerjy.musicxService.Logic.schedule.parse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
 * <p>Запись «без времени» отдельным флагом не помечается (контракт не раздуваем):
 * это случай, когда {@code missing} содержит {@code "time"} (и не содержит {@code "date"}),
 * а {@code startLocal} = дата события с временем 00:00 — дата есть, конкретного
 * времени нет.
 *
 * @param isEvent         сообщение вообще является попыткой создать запись
 * @param title           короткое название, может отсутствовать
 * @param description     дополнительное описание, может отсутствовать
 * @param startLocal      начало в локальном времени пользователя, null если не найдено
 * @param endLocal        конец, если пользователь указал только конец («до 16»)
 * @param durationMinutes длительность в минутах, если указана («на 2 часа»)
 * @param missing         чего не хватает: "date" и/или "time"
 * @param ambiguous       время неоднозначно (например «в 5» без контекста)
 * @param intent          что хочет пользователь: создать, перенести или удалить
 *                        запись; CREATE — историческое поведение
 * @param matchDate       для UPDATE/DELETE: дата существующей записи, опорная точка поиска
 * @param matchTime       для UPDATE/DELETE: время существующей записи, опорная точка поиска
 * @param matchTitle      для UPDATE/DELETE: название (или его часть) существующей записи
 */
public record ParsedEvent(
        boolean isEvent,
        String title,
        String description,
        LocalDateTime startLocal,
        LocalDateTime endLocal,
        Integer durationMinutes,
        Set<String> missing,
        boolean ambiguous,
        Intent intent,
        LocalDate matchDate,
        LocalTime matchTime,
        String matchTitle
) {

    public ParsedEvent {
        missing = missing == null ? Set.of() : Set.copyOf(missing);
        // Дефолт явно в каноническом конструкторе: ответы модели и тесты без
        // новых полей означают «создать запись», как до появления интентов.
        intent = intent == null ? Intent.CREATE : intent;
        matchTitle = matchTitle == null ? null : matchTitle.trim();
        if (matchTitle != null && matchTitle.isEmpty()) {
            matchTitle = null;
        }
    }

    /**
     * Старый (контракт v1) конструктор: только поля создания, интент CREATE,
     * match-поля пустые. Оставлен, чтобы существующие вызовы и тесты валидатора
     * не приходилось переписывать при расширении контракта.
     */
    public ParsedEvent(
            boolean isEvent,
            String title,
            String description,
            LocalDateTime startLocal,
            LocalDateTime endLocal,
            Integer durationMinutes,
            Set<String> missing,
            boolean ambiguous
    ) {
        this(isEvent, title, description, startLocal, endLocal, durationMinutes,
                missing, ambiguous, Intent.CREATE, null, null, null);
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
