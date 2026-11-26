package enerjy.musicxService.messaging;

import enerjy.musicxService.user.userSchedules;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTemplatesTest {

    @Test
    void confirmationContainsAllDetailsInUserZone() {
        var schedule = schedule();
        schedule.setTitle("Урок гитары");
        schedule.setStartAt(Instant.parse("2026-11-27T12:00:00Z")); // 15:00 МСК
        schedule.setEndAt(Instant.parse("2026-11-27T13:00:00Z"));   // 16:00 МСК
        schedule.setTimezone("Europe/Moscow");
        schedule.setNote("кабинет 5");

        String text = MessageTemplates.confirmation(schedule);

        assertThat(text)
                .contains("Запись создана: Урок гитары")
                .contains("Когда: 27.11.2026 15:00")
                .contains("–16:00 (длительность 1 ч)")
                .contains("Описание: кабинет 5");
    }

    @Test
    void confirmationOmitsAbsentParts() {
        var schedule = schedule();
        schedule.setTitle(null);
        schedule.setEndAt(null);
        schedule.setNote(null);
        schedule.setStartAt(Instant.parse("2026-11-27T12:00:00Z"));
        schedule.setTimezone("Europe/Moscow");

        assertThat(MessageTemplates.confirmation(schedule))
                .isEqualTo("Запись создана\nКогда: 27.11.2026 15:00");
    }

    @Test
    void durationIsHumanReadable() {
        assertThat(MessageTemplates.duration(45)).isEqualTo("45 мин");
        assertThat(MessageTemplates.duration(60)).isEqualTo("1 ч");
        assertThat(MessageTemplates.duration(90)).isEqualTo("1 ч 30 мин");
        assertThat(MessageTemplates.duration(120)).isEqualTo("2 ч");
    }

    private static userSchedules schedule() {
        return new userSchedules();
    }
}
