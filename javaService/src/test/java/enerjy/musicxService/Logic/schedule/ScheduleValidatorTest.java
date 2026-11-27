package enerjy.musicxService.Logic.schedule;

import enerjy.musicxService.Logic.schedule.parse.ParsedEvent;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleValidatorTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    /** 26.11.2026 10:00 UTC = 13:00 по Москве. */
    private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");

    private final ScheduleValidator validator = new ScheduleValidator(
            60, 730, 10080, Duration.ofSeconds(60), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void buildsValidScheduleWithDefaultDuration() {
        var result = validator.validate(
                event(LocalDateTime.of(2026, 11, 27, 15, 0)), MOSCOW);

        assertThat(result.title()).isEqualTo("Урок");
        assertThat(result.note()).isNull();
        assertThat(result.startAt()).isEqualTo(Instant.parse("2026-11-27T12:00:00Z"));
        assertThat(result.endAt()).isEqualTo(Instant.parse("2026-11-27T13:00:00Z"));
        assertThat(result.zone()).isEqualTo(MOSCOW);
    }

    @Test
    void notAnEventIsRejected() {
        var event = new ParsedEvent(false, null, null, null, null, null, Set.of(), false);

        assertThatThrownBy(() -> validator.validate(event, MOSCOW))
                .isInstanceOf(ScheduleRejectionException.class)
                .hasMessageContaining("Не похоже");
    }

    @Test
    void ambiguousTimeIsRejected() {
        var event = new ParsedEvent(true, "Урок", null,
                LocalDateTime.of(2026, 11, 27, 5, 0), null, null, Set.of(), true);

        assertThatThrownBy(() -> validator.validate(event, MOSCOW))
                .isInstanceOf(ScheduleRejectionException.class)
                .hasMessageContaining("неоднозначно");
    }

    @Test
    void missingStartIsRejectedWithConcreteHint() {
        var noDate = new ParsedEvent(true, "Урок", null, null, null, null, Set.of("date"), false);
        assertThatThrownBy(() -> validator.validate(noDate, MOSCOW))
                .hasMessageContaining("Не хватает даты")
                .hasMessageNotContaining("времени");

        var noTime = new ParsedEvent(true, "Урок", null, null, null, null, Set.of("time"), false);
        assertThatThrownBy(() -> validator.validate(noTime, MOSCOW))
                .hasMessageContaining("Не хватает времени");

        var noBoth = new ParsedEvent(true, "Урок", null, null, null, null,
                Set.of("date", "time"), false);
        assertThatThrownBy(() -> validator.validate(noBoth, MOSCOW))
                .hasMessageContaining("даты или времени");

        // Модель не заполнила missing — общий подсказка.
        var silent = new ParsedEvent(true, "Урок", null, null, null, null, Set.of(), false);
        assertThatThrownBy(() -> validator.validate(silent, MOSCOW))
                .hasMessageContaining("Не хватает даты или времени");
    }

    @Test
    void allDayPassesWithMidnightReminder() {
        var result = validator.validate(allDay(LocalDate.of(2026, 11, 28)), MOSCOW);

        assertThat(result.allDay()).isTrue();
        assertThat(result.startAt()).isNull();
        assertThat(result.endAt()).isNull();
        assertThat(result.zone()).isEqualTo(MOSCOW);
        // Полночь 28.11 в Москве = 21:00 UTC 27.11.
        assertThat(result.remindAt()).isEqualTo(Instant.parse("2026-11-27T21:00:00Z"));
    }

    @Test
    void allDayTodayIsAcceptedEvenThoughMidnightPassed() {
        // NOW = 13:00 по Москве 26.11: полночь уже прошла, но день ещё жив —
        // проверка прошлого идёт по дню, а не по моменту.
        var result = validator.validate(allDay(LocalDate.of(2026, 11, 26)), MOSCOW);

        assertThat(result.allDay()).isTrue();
        assertThat(result.remindAt()).isEqualTo(Instant.parse("2026-11-25T21:00:00Z"));
    }

    @Test
    void allDayOnHorizonBoundaryIsAccepted() {
        var result = validator.validate(allDay(LocalDate.of(2026, 11, 26).plusDays(730)), MOSCOW);

        assertThat(result.allDay()).isTrue();
    }

    @Test
    void pastAllDayIsRejected() {
        assertThatThrownBy(() -> validator.validate(allDay(LocalDate.of(2026, 11, 25)), MOSCOW))
                .isInstanceOf(ScheduleRejectionException.class)
                .hasMessageContaining("уже прошло");
    }

    @Test
    void allDayBeyondHorizonIsRejected() {
        assertThatThrownBy(() -> validator.validate(allDay(LocalDate.of(2026, 11, 26).plusDays(731)), MOSCOW))
                .hasMessageContaining("Слишком далёкая дата")
                .hasMessageContaining("730");
    }

    @Test
    void dateWithoutAllDayMarkerIsStillRejected() {
        // missing = {"time"} без даты в startLocal — дата неизвестна, безвременной
        // запись назвать нельзя: обычная подсказка про нехватку времени.
        var noDate = new ParsedEvent(true, "Урок", null, null, null, null, Set.of("time"), false);

        assertThatThrownBy(() -> validator.validate(noDate, MOSCOW))
                .hasMessageContaining("Не хватает времени");
    }

    @Test
    void pastStartIsRejected() {
        var past = event(instantInMoscow(NOW.minus(Duration.ofMinutes(5))));

        assertThatThrownBy(() -> validator.validate(past, MOSCOW))
                .isInstanceOf(ScheduleRejectionException.class)
                .hasMessageContaining("уже прошло");
    }

    @Test
    void startWithinToleranceIsAccepted() {
        var justPast = event(instantInMoscow(NOW.minus(Duration.ofSeconds(30))));

        assertThatCode(() -> validator.validate(justPast, MOSCOW))
                .doesNotThrowAnyException();
    }

    @Test
    void startBeyondHorizonIsRejected() {
        var tooFar = event(instantInMoscow(NOW.plus(Duration.ofDays(731))));

        assertThatThrownBy(() -> validator.validate(tooFar, MOSCOW))
                .hasMessageContaining("Слишком далёкая дата")
                .hasMessageContaining("730");
    }

    @Test
    void startOnHorizonBoundaryIsAccepted() {
        var onBoundary = event(instantInMoscow(NOW.plus(Duration.ofDays(730))));

        assertThatCode(() -> validator.validate(onBoundary, MOSCOW))
                .doesNotThrowAnyException();
    }

    @Test
    void explicitDurationIsUsed() {
        var result = validator.validate(
                withDuration(LocalDateTime.of(2026, 11, 27, 15, 0), 120), MOSCOW);

        assertThat(result.endAt()).isEqualTo(Instant.parse("2026-11-27T14:00:00Z"));
    }

    @Test
    void nonPositiveDurationFallsBackToDefault() {
        var result = validator.validate(withDuration(LocalDateTime.of(2026, 11, 27, 15, 0), 0), MOSCOW);

        assertThat(result.endAt()).isEqualTo(Instant.parse("2026-11-27T13:00:00Z"));
    }

    @Test
    void explicitEndWinsOverDuration() {
        var start = LocalDateTime.of(2026, 11, 27, 15, 0);
        var event = new ParsedEvent(true, "Урок", null, start,
                start.plusMinutes(90), 30, Set.of(), false);

        var result = validator.validate(event, MOSCOW);

        assertThat(result.endAt()).isEqualTo(Instant.parse("2026-11-27T13:30:00Z"));
    }

    @Test
    void endBeforeOrEqualToStartIsRejected() {
        var start = LocalDateTime.of(2026, 11, 27, 15, 0);
        var before = new ParsedEvent(true, "Урок", null, start, start.minusMinutes(30), null, Set.of(), false);
        assertThatThrownBy(() -> validator.validate(before, MOSCOW))
                .hasMessageContaining("позже начала");

        var equal = new ParsedEvent(true, "Урок", null, start, start, null, Set.of(), false);
        assertThatThrownBy(() -> validator.validate(equal, MOSCOW))
                .hasMessageContaining("позже начала");
    }

    @Test
    void tooLongDurationIsRejected() {
        var byDuration = withDuration(LocalDateTime.of(2026, 11, 27, 15, 0), 10081);
        assertThatThrownBy(() -> validator.validate(byDuration, MOSCOW))
                .hasMessageContaining("Длительность")
                .hasMessageContaining("10080");

        var start = LocalDateTime.of(2026, 11, 27, 15, 0);
        var byEnd = new ParsedEvent(true, "Урок", null, start,
                start.plusMinutes(10081), null, Set.of(), false);
        assertThatThrownBy(() -> validator.validate(byEnd, MOSCOW))
                .hasMessageContaining("Длительность");
    }

    @Test
    void durationOnMaxBoundaryIsAccepted() {
        var result = validator.validate(
                withDuration(LocalDateTime.of(2026, 11, 27, 15, 0), 10080), MOSCOW);

        assertThat(Duration.between(result.startAt(), result.endAt()).toMinutes()).isEqualTo(10080);
    }

    @Test
    void longFieldsAreTruncated() {
        var event = new ParsedEvent(true,
                "т".repeat(250), "о".repeat(1200),
                LocalDateTime.of(2026, 11, 27, 15, 0), null, null, Set.of(), false);

        var result = validator.validate(event, MOSCOW);

        assertThat(result.title().length()).isEqualTo(ScheduleValidator.TITLE_MAX_LENGTH);
        assertThat(result.note().length()).isEqualTo(ScheduleValidator.NOTE_MAX_LENGTH);
    }

    @Test
    void blankFieldsBecomeNull() {
        var event = new ParsedEvent(true, "   ", "  ",
                LocalDateTime.of(2026, 11, 27, 15, 0), null, null, Set.of(), false);

        var result = validator.validate(event, MOSCOW);

        assertThat(result.title()).isNull();
        assertThat(result.note()).isNull();
    }

    @Test
    void invalidLimitsAreRejectedAtConstruction() {
        assertThatThrownBy(() -> new ScheduleValidator(0, 730, 10080, Duration.ofSeconds(60), fixedClock()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScheduleValidator(60, 0, 10080, Duration.ofSeconds(60), fixedClock()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScheduleValidator(60, 730, 10080, Duration.ofSeconds(-1), fixedClock()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ParsedEvent event(LocalDateTime startLocal) {
        return new ParsedEvent(true, "Урок", null, startLocal, null, null, Set.of(), false);
    }

    /** Безвременная запись по контракту: дата с 00:00 в startLocal, время в missing. */
    private static ParsedEvent allDay(LocalDate date) {
        return new ParsedEvent(true, "Репетиция", null,
                date.atStartOfDay(), null, null, Set.of("time"), false);
    }

    private static ParsedEvent withDuration(LocalDateTime startLocal, int durationMinutes) {
        return new ParsedEvent(true, "Урок", null, startLocal, null, durationMinutes, Set.of(), false);
    }

    private static LocalDateTime instantInMoscow(Instant instant) {
        return instant.atZone(MOSCOW).toLocalDateTime();
    }

    private static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }
}
