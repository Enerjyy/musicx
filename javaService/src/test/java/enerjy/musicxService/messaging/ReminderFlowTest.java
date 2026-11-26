package enerjy.musicxService.messaging;

import enerjy.musicxService.messaging.contract.Channels;
import enerjy.musicxService.user.CommandJournalRepo;
import enerjy.musicxService.user.CommandPurpose;
import enerjy.musicxService.user.Reminder;
import enerjy.musicxService.user.ReminderKind;
import enerjy.musicxService.user.ReminderRepo;
import enerjy.musicxService.user.ReminderStatus;
import enerjy.musicxService.user.ScheduleStatus;
import enerjy.musicxService.user.userSchedules;
import enerjy.musicxService.user.userSchedulesRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Планировщик напоминаний на реальной БД (H2): захват due-напоминаний,
 * возврат зависших SENDING, EXPIRED при прошедшем start_at и уникальность
 * (schedule_id, kind). Публикацию SendMessage проверяет юнит-тест.
 */
@SpringBootTest
@Transactional
class ReminderFlowTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    @Autowired
    private ReminderRepo reminders;

    @Autowired
    private userSchedulesRepo schedulesRepo;

    @Autowired
    private CommandJournalRepo commandJournal;

    @Autowired
    private ReminderProcessor processor;

    @Test
    void claimsDueReminderOnRealDatabase() {
        var schedule = saveSchedule(Instant.now().plus(Duration.ofHours(2)), "42-" + System.nanoTime());
        var saved = reminders.saveAndFlush(reminder(schedule, Instant.now().minusSeconds(300), 0, null));

        processor.processDue();

        var reloaded = reminders.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ReminderStatus.SENDING);
        assertThat(reloaded.getAttempts()).isEqualTo(1);
        assertThat(reloaded.getClaimedAt()).isNotNull();

        var journal = commandJournal.findFirstByRelatedIdAndPurposeOrderByCreatedAtDesc(
                reloaded.getId(), CommandPurpose.REMINDER);
        assertThat(journal).isPresent();
        assertThat(journal.get().getRelatedId()).isEqualTo(reloaded.getId());
        assertThat(journal.get().getType()).isEqualTo("SEND_MESSAGE");
    }

    @Test
    void stuckSendingIsRescuedAndReclaimedOnNextTick() {
        var schedule = saveSchedule(Instant.now().plus(Duration.ofHours(2)), "42-" + System.nanoTime());
        var stuck = reminder(schedule, Instant.now().minusSeconds(300), 1, Instant.now().minus(Duration.ofMinutes(15)));
        stuck.setStatus(ReminderStatus.SENDING);
        var saved = reminders.saveAndFlush(stuck);

        processor.processDue();

        var reloaded = reminders.findById(saved.getId()).orElseThrow();
        // Вернули в PENDING и на том же тике забрали снова: remind_at уже в прошлом.
        assertThat(reloaded.getStatus()).isEqualTo(ReminderStatus.SENDING);
        assertThat(reloaded.getAttempts()).isEqualTo(2);
    }

    @Test
    void reminderIsExpiredWhenEventAlreadyStarted() {
        var schedule = saveSchedule(Instant.now().minus(Duration.ofHours(1)), "42-" + System.nanoTime());
        var saved = reminders.saveAndFlush(reminder(schedule, Instant.now().minus(Duration.ofHours(4)), 0, null));

        processor.processDue();

        var reloaded = reminders.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ReminderStatus.EXPIRED);
        assertThat(reloaded.getAttempts()).isZero();
    }

    @Test
    void duplicateReminderIsRejectedByUniqueKey() {
        var schedule = saveSchedule(Instant.now().plus(Duration.ofHours(2)), "42-" + System.nanoTime());
        reminders.saveAndFlush(reminder(schedule, Instant.now().minusSeconds(300), 0, null));

        var duplicate = reminder(schedule, Instant.now().minusSeconds(300), 0, null);

        assertThatThrownBy(() -> reminders.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private userSchedules saveSchedule(Instant startAt, String conversationId) {
        var schedule = new userSchedules();
        schedule.setUserId(99001L);
        schedule.setDate(startAt.atZone(MOSCOW).toLocalDate());
        schedule.setTime(startAt.atZone(MOSCOW).toLocalTime());
        schedule.setTitle("Урок гитары");
        schedule.setStartAt(startAt);
        schedule.setEndAt(startAt.plus(Duration.ofHours(1)));
        schedule.setTimezone(MOSCOW.getId());
        schedule.setStatus(ScheduleStatus.ACTIVE);
        schedule.setSourceChannel(Channels.TELEGRAM);
        schedule.setSourceConversationId(conversationId);
        schedule.setSourceMessageId("100-" + System.nanoTime());
        schedule.setCreatedAt(Instant.now());
        return schedulesRepo.saveAndFlush(schedule);
    }

    private static Reminder reminder(userSchedules schedule, Instant remindAt, int attempts, Instant claimedAt) {
        var reminder = new Reminder();
        reminder.setScheduleId(schedule.getSceduleId());
        reminder.setKind(ReminderKind.T_MINUS_3H);
        reminder.setRemindAt(remindAt);
        reminder.setStatus(ReminderStatus.PENDING);
        reminder.setAttempts(attempts);
        reminder.setClaimedAt(claimedAt);
        return reminder;
    }
}
