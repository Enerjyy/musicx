package enerjy.musicxService.LlmRequests;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LessonParserNormalizationTest {

    @Test
    void parsesIsoDateAndTime() {
        assertThat(LessonParser.parseDate("2026-11-25")).isEqualTo(LocalDate.of(2026, 11, 25));
        assertThat(LessonParser.parseTime("15:00")).isEqualTo(LocalTime.of(15, 0));
    }

    @Test
    void parsesEnglishMonthNameDateFromModel() {
        assertThat(LessonParser.parseDate("November 25, 2026"))
                .isEqualTo(LocalDate.of(2026, 11, 25));
    }

    @Test
    void parsesRussianMonthNameDate() {
        assertThat(LessonParser.parseDate("25 ноября 2026 года"))
                .isEqualTo(LocalDate.of(2026, 11, 25));
    }

    @Test
    void parsesDateWithTimeSuffix() {
        assertThat(LessonParser.parseDate("November 25, 2026 15:00"))
                .isEqualTo(LocalDate.of(2026, 11, 25));
        assertThat(LessonParser.parseDate("25 ноября 2026 года 15:00"))
                .isEqualTo(LocalDate.of(2026, 11, 25));
        assertThat(LessonParser.parseDate("2026-11-25T15:00:00"))
                .isEqualTo(LocalDate.of(2026, 11, 25));
    }

    @Test
    void parsesDottedAndSlashedDates() {
        assertThat(LessonParser.parseDate("25.11.2026")).isEqualTo(LocalDate.of(2026, 11, 25));
        assertThat(LessonParser.parseDate("25/11/2026")).isEqualTo(LocalDate.of(2026, 11, 25));
        assertThat(LessonParser.parseDate("11/25/2026")).isEqualTo(LocalDate.of(2026, 11, 25));
    }

    @Test
    void parsesAmPmAndTimeWithZone() {
        assertThat(LessonParser.parseTime("3:00 PM")).isEqualTo(LocalTime.of(15, 0));
        assertThat(LessonParser.parseTime("15:00 MSK")).isEqualTo(LocalTime.of(15, 0));
        assertThat(LessonParser.parseTime("15.00")).isEqualTo(LocalTime.of(15, 0));
        assertThat(LessonParser.parseTime("7:05")).isEqualTo(LocalTime.of(7, 5));
    }

    @Test
    void rejectsGarbageDate() {
        assertThatThrownBy(() -> LessonParser.parseDate("not a date"))
                .isInstanceOf(LlmRequestException.class);
    }

    @Test
    void rejectsGarbageTime() {
        assertThatThrownBy(() -> LessonParser.parseTime("когда-нибудь"))
                .isInstanceOf(LlmRequestException.class);
    }

    @Test
    void returnsNullForBlankFields() {
        assertThat(LessonParser.parseDate(null)).isNull();
        assertThat(LessonParser.parseTime(null)).isNull();
    }
}
