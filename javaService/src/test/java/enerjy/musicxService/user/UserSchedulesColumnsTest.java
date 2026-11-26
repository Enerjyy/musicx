package enerjy.musicxService.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Новые колонки расписания (Шаг 4): абсолютное время, снимок зоны, статус,
 * источник для дедупликации. Заодно проверяется, что Flyway-миграции V6+V7
 * накатываются, а Hibernate validate согласен со схемой.
 */
@SpringBootTest
@Transactional
class UserSchedulesColumnsTest {

    @Autowired
    private userSchedulesRepo repo;

    @Test
    void persistsNewTimeColumns() {
        var entry = newEntry("TELEGRAM", "conv-" + System.nanoTime(), "1001");
        entry.setStartAt(Instant.parse("2026-11-27T12:00:00Z"));
        entry.setEndAt(Instant.parse("2026-11-27T13:30:00Z"));
        entry.setTimezone("Europe/Berlin");
        entry.setCreatedAt(Instant.parse("2026-11-26T10:00:00Z"));

        var saved = repo.saveAndFlush(entry);

        var loaded = repo.findById(saved.getSceduleId()).orElseThrow();
        assertThat(loaded.getStartAt()).isEqualTo(Instant.parse("2026-11-27T12:00:00Z"));
        assertThat(loaded.getEndAt()).isEqualTo(Instant.parse("2026-11-27T13:30:00Z"));
        assertThat(loaded.getTimezone()).isEqualTo("Europe/Berlin");
        assertThat(loaded.getCreatedAt()).isEqualTo(Instant.parse("2026-11-26T10:00:00Z"));
        assertThat(loaded.getStatus()).isEqualTo(ScheduleStatus.ACTIVE);
        // Существующие поля не сломаны.
        assertThat(loaded.getDate()).isEqualTo(LocalDate.of(2026, 11, 27));
        assertThat(loaded.getTime()).isEqualTo(LocalTime.of(15, 0));
    }

    @Test
    void statusDefaultsToActiveWhenNotSet() {
        var entry = newEntry("TELEGRAM", "conv-" + System.nanoTime(), "1002");
        entry.setStartAt(Instant.parse("2026-11-27T12:00:00Z"));

        var saved = repo.saveAndFlush(entry);

        assertThat(repo.findById(saved.getSceduleId()).orElseThrow().getStatus())
                .isEqualTo(ScheduleStatus.ACTIVE);
    }

    @Test
    void duplicateSourceTripleIsRejected() {
        String conversation = "conv-" + System.nanoTime();
        var first = newEntry("TELEGRAM", conversation, "2001");
        first.setStartAt(Instant.parse("2026-11-27T12:00:00Z"));
        repo.saveAndFlush(first);

        var duplicate = newEntry("TELEGRAM", conversation, "2001");
        duplicate.setStartAt(Instant.parse("2026-11-27T18:00:00Z"));

        assertThatThrownBy(() -> repo.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Тройка (channel, conversation, message) с NULL не ограничивается индексом. */
    @Test
    void entriesWithoutSourceCanBeDuplicated() {
        var one = newEntry(null, null, null);
        one.setStartAt(Instant.parse("2026-11-27T12:00:00Z"));
        var two = newEntry(null, null, null);
        two.setStartAt(Instant.parse("2026-11-27T14:00:00Z"));

        assertThat(repo.saveAndFlush(one).getSceduleId()).isNotNull();
        assertThat(repo.saveAndFlush(two).getSceduleId()).isNotNull();
    }

    private static userSchedules newEntry(String channel, String conversation, String messageId) {
        var entry = new userSchedules();
        entry.setUserId(99001L);
        entry.setDate(LocalDate.of(2026, 11, 27));
        entry.setTime(LocalTime.of(15, 0));
        entry.setTitle("Урок");
        entry.setSourceChannel(channel);
        entry.setSourceConversationId(conversation);
        entry.setSourceMessageId(messageId);
        return entry;
    }
}
