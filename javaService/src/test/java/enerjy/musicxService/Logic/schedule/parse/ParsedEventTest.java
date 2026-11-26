package enerjy.musicxService.Logic.schedule.parse;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParsedEventTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void convertsLocalStartToInstantInUserZone() {
        var event = event(LocalDateTime.of(2026, 11, 27, 15, 0));

        assertThat(event.startAt(MOSCOW)).isEqualTo(Instant.parse("2026-11-27T12:00:00Z"));
    }

    @Test
    void nonexistentTimeInSpringGapShiftsForward() {
        // 29.03.2026 в 02:00 Берлин переводит стрелки на 03:00: местного времени 02:30 не существует.
        var event = event(LocalDateTime.of(2026, 3, 29, 2, 30));

        assertThat(event.startAt(BERLIN)).isEqualTo(Instant.parse("2026-03-29T01:30:00Z"));
        // Модель не решает DST: Java сдвигает время на длину перехода (02:30 → 03:30).
        var zoned = LocalDateTime.of(2026, 3, 29, 2, 30).atZone(BERLIN);
        assertThat(zoned.toLocalTime()).isEqualTo(java.time.LocalTime.of(3, 30));
        assertThat(zoned.getOffset()).isEqualTo(java.time.ZoneOffset.ofHours(2));
    }

    @Test
    void ambiguousTimeInAutumnOverlapUsesEarlierInstant() {
        // 25.10.2026 в 03:00 Берлин переводит стрелки на 02:00: время 02:30 наступает дважды.
        var event = event(LocalDateTime.of(2026, 10, 25, 2, 30));

        // Берём первое (раннее) вхождение: 02:30 +02:00 = 00:30 UTC.
        assertThat(event.startAt(BERLIN)).isEqualTo(Instant.parse("2026-10-25T00:30:00Z"));
    }

    @Test
    void zoneWithoutDstStaysStable() {
        var event = event(LocalDateTime.of(2026, 6, 15, 9, 0));

        assertThat(event.startAt(MOSCOW)).isEqualTo(Instant.parse("2026-06-15T06:00:00Z"));
        assertThat(event.startAt(BERLIN)).isEqualTo(Instant.parse("2026-06-15T07:00:00Z"));
    }

    @Test
    void endAtUsesSameRules() {
        var event = event(null, LocalDateTime.of(2026, 10, 25, 2, 30));

        assertThat(event.endAt(BERLIN)).isEqualTo(Instant.parse("2026-10-25T00:30:00Z"));
    }

    @Test
    void absentStartIsRejected() {
        var event = event(null);

        assertThatThrownBy(() -> event.startAt(MOSCOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("startLocal");
        assertThatThrownBy(() -> event.endAt(MOSCOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("endLocal");
    }

    @Test
    void missingIsNeverNull() {
        var event = new ParsedEvent(true, null, null, null, null, null, null, false);

        assertThat(event.missing()).isEmpty();
    }

    @Test
    void missingIsCopiedAndUnmodifiable() {
        var source = new LinkedHashSet<String>();
        source.add("date");

        var event = new ParsedEvent(true, null, null, null, null, null, source, false);

        assertThatThrownBy(() -> event.missing().add("time"))
                .isInstanceOf(UnsupportedOperationException.class);
        source.add("time");
        assertThat(event.missing()).containsExactly("date");
    }

    private static ParsedEvent event(LocalDateTime start) {
        return event(start, null);
    }

    private static ParsedEvent event(LocalDateTime start, LocalDateTime end) {
        return new ParsedEvent(true, "Урок", null, start, end, null, Set.of(), false);
    }
}
