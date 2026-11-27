package enerjy.musicxService.Logic.schedule;

import enerjy.musicxService.Logic.schedule.parse.ParsedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;

/**
 * Правила приёмки события в календарь (Шаг 4).
 *
 * Валидатор принимает {@link ParsedEvent} и возвращает {@link ValidatedSchedule}
 * либо бросает {@link ScheduleRejectionException} с подсказкой для пользователя.
 * Диалогов-уточнений нет: если данных не хватает, запись не создаётся.
 *
 * Время считаем в зоне пользователя через {@link ParsedEvent#startAt(ZoneId)},
 * поэтому переходы на летнее время обрабатывает java.time, а не модель.
 *
 * <p>Записи «весь день» принимаются без времени: в {@link ParsedEvent} это
 * {@code startLocal} = дата с временем 00:00 и {@code missing}, содержащий
 * {@code "time"} (и не содержащий {@code "date"}). Проверяются только прошлое
 * (по дню, а не по моменту: событие «сегодня весь день» ещё живо даже вечером)
 * и горизонт планирования; напоминание — ровно 00:00 дня события в зоне пользователя.
 */
@Service
public class ScheduleValidator {

    /** Совпадает с длиной колонки title. */
    public static final int TITLE_MAX_LENGTH = 200;
    /** Совпадает с длиной колонки note. */
    public static final int NOTE_MAX_LENGTH = 1000;

    public static final String HINT_NOT_AN_EVENT =
            "Не похоже на запись в календарь. Опишите событие целиком, например: «завтра в 15:00 урок гитары на час».";
    public static final String HINT_AMBIGUOUS =
            "Время неоднозначно. Укажите дату и время полностью, например: «завтра в 15:00 урок гитары на час».";
    public static final String HINT_MISSING =
            "Не хватает даты или времени. Напишите целиком, например: «завтра в 15:00 урок гитары на час».";
    public static final String HINT_MISSING_DATE =
            "Не хватает даты. Напишите целиком, например: «завтра в 15:00 урок гитары на час».";
    public static final String HINT_MISSING_TIME =
            "Не хватает времени. Укажите время, например: «завтра в 15:00», "
                    + "или назначьте на весь день: «завтра репетиция, весь день».";
    public static final String HINT_IN_THE_PAST =
            "Это время уже прошло. Укажите будущее время, например: «завтра в 15:00 урок гитары на час».";
    public static final String HINT_END_BEFORE_START =
            "Конец должен быть позже начала. Например: «завтра с 15:00 до 16:00».";

    private static final String HINT_TOO_FAR =
            "Слишком далёкая дата: планировать можно не больше чем на %d дней вперёд.";
    private static final String HINT_TOO_LONG =
            "Длительность слишком большая: максимум %d минут.";

    private final Clock clock;
    private final int defaultDurationMinutes;
    private final int maxHorizonDays;
    private final int maxDurationMinutes;
    private final Duration pastTolerance;

    @Autowired
    public ScheduleValidator(
            @Value("${app.calendar.default-duration-minutes:60}") int defaultDurationMinutes,
            @Value("${app.calendar.max-horizon-days:730}") int maxHorizonDays,
            @Value("${app.calendar.max-duration-minutes:10080}") int maxDurationMinutes,
            @Value("${app.calendar.past-tolerance-seconds:60}") long pastToleranceSeconds
    ) {
        this(defaultDurationMinutes, maxHorizonDays, maxDurationMinutes,
                Duration.ofSeconds(pastToleranceSeconds), Clock.systemUTC());
    }

    ScheduleValidator(
            int defaultDurationMinutes,
            int maxHorizonDays,
            int maxDurationMinutes,
            Duration pastTolerance,
            Clock clock
    ) {
        if (defaultDurationMinutes <= 0) {
            throw new IllegalArgumentException("default duration must be positive");
        }
        if (maxHorizonDays <= 0 || maxDurationMinutes <= 0 || pastTolerance.isNegative()) {
            throw new IllegalArgumentException("limits must be positive");
        }
        this.defaultDurationMinutes = defaultDurationMinutes;
        this.maxHorizonDays = maxHorizonDays;
        this.maxDurationMinutes = maxDurationMinutes;
        this.pastTolerance = pastTolerance;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ValidatedSchedule validate(ParsedEvent event, ZoneId userZone) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(userZone, "userZone");

        if (!event.isEvent()) {
            throw reject(HINT_NOT_AN_EVENT);
        }
        if (event.ambiguous()) {
            throw reject(HINT_AMBIGUOUS);
        }
        if (isAllDay(event)) {
            return validateAllDay(event, userZone);
        }
        if (event.startLocal() == null) {
            throw reject(hintForMissing(event.missing()));
        }

        Instant now = Instant.now(clock);
        Instant start = event.startAt(userZone);
        if (start.isBefore(now.minus(pastTolerance))) {
            throw reject(HINT_IN_THE_PAST);
        }
        if (start.isAfter(now.plus(Duration.ofDays(maxHorizonDays)))) {
            throw reject(HINT_TOO_FAR.formatted(maxHorizonDays));
        }

        Instant end = event.endLocal() != null
                ? event.endAt(userZone)
                : start.plus(Duration.ofMinutes(resolveDuration(event)));

        long durationMinutes = Duration.between(start, end).toMinutes();
        if (durationMinutes <= 0) {
            throw reject(HINT_END_BEFORE_START);
        }
        if (durationMinutes > maxDurationMinutes) {
            throw reject(HINT_TOO_LONG.formatted(maxDurationMinutes));
        }

        return new ValidatedSchedule(
                truncate(event.title(), TITLE_MAX_LENGTH),
                truncate(event.description(), NOTE_MAX_LENGTH),
                start,
                end,
                userZone
        );
    }

    /**
     * «Весь день»: дата есть (startLocal = дата и 00:00), конкретного времени нет.
     * Не «startLocal == null»: дату иначе неоткуда взять, а без неё не проверить
     * прошлое/горизонт и не посчитать полуночное напоминание.
     */
    private static boolean isAllDay(ParsedEvent event) {
        return event.startLocal() != null
                && event.missing().contains("time")
                && !event.missing().contains("date");
    }

    /**
     * Приёмка безвременной записи: прошлое и горизонт проверяем по дню, а не по
     * моменту — «сегодня весь день» валидно и вечером; endAt/duration не проверяем,
     * потому что их у такой записи нет. {@code remindAt} = 00:00 дня события:
     * отправит ли его процессор (день ещё не наступил) — забота этапа напоминаний,
     * здесь фиксируется только сам момент.
     */
    private ValidatedSchedule validateAllDay(ParsedEvent event, ZoneId userZone) {
        Instant now = Instant.now(clock);
        LocalDate date = event.startLocal().toLocalDate();
        LocalDate today = now.atZone(userZone).toLocalDate();
        Instant dayStart = date.atStartOfDay(userZone).toInstant();

        if (date.isBefore(today)) {
            throw reject(HINT_IN_THE_PAST);
        }
        if (dayStart.isAfter(now.plus(Duration.ofDays(maxHorizonDays)))) {
            throw reject(HINT_TOO_FAR.formatted(maxHorizonDays));
        }

        return new ValidatedSchedule(
                truncate(event.title(), TITLE_MAX_LENGTH),
                truncate(event.description(), NOTE_MAX_LENGTH),
                null,
                null,
                userZone,
                true,
                dayStart
        );
    }

    /** Задана длительность — она и используется, иначе значение по умолчанию. */
    private int resolveDuration(ParsedEvent event) {
        Integer duration = event.durationMinutes();
        return duration != null && duration > 0 ? duration : defaultDurationMinutes;
    }

    private static String hintForMissing(Set<String> missing) {
        if (missing.contains("date") && missing.contains("time")) {
            return HINT_MISSING;
        }
        if (missing.contains("date")) {
            return HINT_MISSING_DATE;
        }
        if (missing.contains("time")) {
            return HINT_MISSING_TIME;
        }
        return HINT_MISSING;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null
                : trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private static ScheduleRejectionException reject(String hint) {
        return new ScheduleRejectionException(hint);
    }
}
