package enerjy.musicxService.messaging;

import enerjy.musicxService.messaging.channel.ChannelRegistry;
import enerjy.musicxService.messaging.channel.OutboundChannel;
import enerjy.musicxService.messaging.contract.Channels;
import enerjy.musicxService.messaging.contract.OutboundCommand;
import enerjy.musicxService.messaging.contract.SendMessage;
import enerjy.musicxService.user.CommandDeliveryStatus;
import enerjy.musicxService.user.CommandJournalEntry;
import enerjy.musicxService.user.CommandJournalRepo;
import enerjy.musicxService.user.CommandPurpose;
import enerjy.musicxService.user.Reminder;
import enerjy.musicxService.user.ReminderKind;
import enerjy.musicxService.user.ReminderRepo;
import enerjy.musicxService.user.ReminderStatus;
import enerjy.musicxService.user.userSchedules;
import enerjy.musicxService.user.userSchedulesRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReminderProcessorTest {

    /** 26.11.2026 10:00 UTC = 13:00 МСК. */
    private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");
    private static final Duration STUCK_AFTER = Duration.ofMinutes(10);
    private static final Duration OFFSET = Duration.ofHours(3);
    private static final Duration MIDNIGHT_GRACE = Duration.ofHours(6);

    @Mock
    private ReminderRepo reminders;
    @Mock
    private userSchedulesRepo schedulesRepo;
    @Mock
    private CommandJournalRepo commandJournal;
    @Mock
    private OutboundChannel channel;

    private ReminderProcessor processor;

    @BeforeEach
    void setUp() {
        when(channel.id()).thenReturn(Channels.TELEGRAM);
        processor = new ReminderProcessor(
                reminders, schedulesRepo, commandJournal,
                new ChannelRegistry(List.of(channel)), stubTransactions(),
                Clock.fixed(NOW, ZoneOffset.UTC), STUCK_AFTER, OFFSET, MIDNIGHT_GRACE, 50);
    }

    @AfterEach
    void clearSynchronizations() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void claimsDueReminderAndSendsAfterCommitToSameConversation() {
        var reminder = dueReminder(5L, 11L);
        when(reminders.returnStuckToPending(any(), any(), any())).thenReturn(0);
        when(reminders.expirePassed(any(), any(), any(), any(), any())).thenReturn(0);
        when(reminders.lockDue(eq(ReminderStatus.PENDING), eq(NOW), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(reminder));
        when(schedulesRepo.findById(11L)).thenReturn(Optional.of(schedule()));

        TransactionSynchronizationManager.initSynchronization();
        processor.processDue();

        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.SENDING);
        assertThat(reminder.getAttempts()).isEqualTo(1);
        assertThat(reminder.getClaimedAt()).isEqualTo(NOW);

        var savedCommand = ArgumentCaptor.forClass(CommandJournalEntry.class);
        verify(commandJournal).save(savedCommand.capture());
        var command = savedCommand.getValue();
        assertThat(command.getType()).isEqualTo(SendMessage.TYPE);
        assertThat(command.getPurpose()).isEqualTo(CommandPurpose.REMINDER);
        assertThat(command.getRelatedId()).isEqualTo(5L);
        assertThat(command.getStatus()).isEqualTo(CommandDeliveryStatus.PENDING);
        assertThat(command.getChannel()).isEqualTo(Channels.TELEGRAM);
        assertThat(command.getConversationId()).isEqualTo("42");

        // До коммита ничего не опубликовано.
        verify(channel, never()).publish(any());

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        var published = ArgumentCaptor.forClass(OutboundCommand.class);
        verify(channel, atLeastOnce()).publish(published.capture());
        var send = (SendMessage) published.getValue();
        assertThat(send.commandId()).isEqualTo(command.getCommandId());
        assertThat(send.conversationId()).isEqualTo("42");
        assertThat(send.text())
                .contains("Напоминание")
                .contains("через 3 ч")
                .contains("«Урок гитары»")
                .contains("26.11.2026 15:00");
    }

    @Test
    void nothingHappensWithoutDueReminders() {
        when(reminders.returnStuckToPending(any(), any(), any())).thenReturn(0);
        when(reminders.expirePassed(any(), any(), any(), any(), any())).thenReturn(0);
        when(reminders.lockDue(any(), any(), any(), any(), any(Pageable.class))).thenReturn(List.of());

        processor.processDue();

        verify(commandJournal, never()).save(any());
        verify(channel, never()).publish(any());
    }

    @Test
    void rescueAndExpireRunsWithConfiguredDeadlines() {
        when(reminders.returnStuckToPending(any(), any(), any())).thenReturn(0);
        when(reminders.expirePassed(any(), any(), any(), any(), any())).thenReturn(0);
        when(reminders.lockDue(any(), any(), any(), any(), any(Pageable.class))).thenReturn(List.of());

        processor.processDue();

        verify(reminders).returnStuckToPending(
                ReminderStatus.PENDING, ReminderStatus.SENDING, NOW.minus(STUCK_AFTER));
        verify(reminders).expirePassed(
                ReminderStatus.EXPIRED, ReminderStatus.PENDING, NOW,
                ReminderKind.MIDNIGHT, NOW.minus(MIDNIGHT_GRACE));
    }

    @Test
    void missingScheduleFailsReminderWithoutSending() {
        var reminder = dueReminder(5L, 11L);
        when(reminders.returnStuckToPending(any(), any(), any())).thenReturn(0);
        when(reminders.expirePassed(any(), any(), any(), any(), any())).thenReturn(0);
        when(reminders.lockDue(any(), any(), any(), any(), any(Pageable.class))).thenReturn(List.of(reminder));
        when(schedulesRepo.findById(11L)).thenReturn(Optional.empty());

        processor.processDue();

        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.EXPIRED);
        verify(commandJournal, never()).save(any());
        verify(channel, never()).publish(any());
    }

    @Test
    void unsupportedChannelFailsReminder() {
        var reminder = dueReminder(5L, 11L);
        when(reminders.returnStuckToPending(any(), any(), any())).thenReturn(0);
        when(reminders.expirePassed(any(), any(), any(), any(), any())).thenReturn(0);
        when(reminders.lockDue(any(), any(), any(), any(), any(Pageable.class))).thenReturn(List.of(reminder));
        var schedule = schedule();
        schedule.setSourceChannel("WHATSAPP");
        when(schedulesRepo.findById(11L)).thenReturn(Optional.of(schedule));

        processor.processDue();

        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.FAILED);
        verify(commandJournal, never()).save(any());
        verify(channel, never()).publish(any());
    }

    @Test
    void invalidConfigIsRejected() {
        assertThatThrownBy(() -> new ReminderProcessor(
                reminders, schedulesRepo, commandJournal, null, stubTransactions(),
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ZERO, OFFSET, MIDNIGHT_GRACE, 50))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReminderProcessor(
                reminders, schedulesRepo, commandJournal, null, stubTransactions(),
                Clock.fixed(NOW, ZoneOffset.UTC), STUCK_AFTER, OFFSET, MIDNIGHT_GRACE, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReminderProcessor(
                reminders, schedulesRepo, commandJournal, null, stubTransactions(),
                Clock.fixed(NOW, ZoneOffset.UTC), STUCK_AFTER, Duration.ofHours(-1), MIDNIGHT_GRACE, 50))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReminderProcessor(
                reminders, schedulesRepo, commandJournal, null, stubTransactions(),
                Clock.fixed(NOW, ZoneOffset.UTC), STUCK_AFTER, OFFSET, Duration.ZERO, 50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void claimsDueMidnightReminderAndSendsAllDayText() {
        var reminder = dueReminder(5L, 11L);
        reminder.setKind(ReminderKind.MIDNIGHT);
        // NOW = 10:00 UTC; полночь уже наступила (remindAt = час назад) —
        // внутри grace-окна (6 часов), окно lockDue такое и вернёт.
        reminder.setRemindAt(NOW.minus(Duration.ofHours(1)));
        when(reminders.returnStuckToPending(any(), any(), any())).thenReturn(0);
        when(reminders.expirePassed(any(), any(), any(), any(), any())).thenReturn(0);
        when(reminders.lockDue(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(reminder));
        var schedule = schedule();
        schedule.setStartAt(null);
        schedule.setDate(LocalDate.of(2026, 11, 26));
        schedule.setEndAt(null);
        when(schedulesRepo.findById(11L)).thenReturn(Optional.of(schedule));

        TransactionSynchronizationManager.initSynchronization();
        processor.processDue();

        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.SENDING);
        verify(commandJournal).save(any(CommandJournalEntry.class));

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        var published = ArgumentCaptor.forClass(OutboundCommand.class);
        verify(channel, atLeastOnce()).publish(published.capture());
        var send = (SendMessage) published.getValue();
        assertThat(send.text())
                .contains("Напоминание: сегодня")
                .contains("«Урок гитары»")
                .contains("весь день");
    }

    @Test
    void midnightReminderPastGraceWindowExpiresWithoutSending() {
        var reminder = dueReminder(5L, 11L);
        reminder.setKind(ReminderKind.MIDNIGHT);
        // Полночь события была больше grace назад — тик её уже не должен был
        // вернуть, но защитная ветка claim() гасит и такую (долгий простой).
        reminder.setRemindAt(NOW.minus(MIDNIGHT_GRACE).minusSeconds(60));
        when(reminders.returnStuckToPending(any(), any(), any())).thenReturn(0);
        when(reminders.expirePassed(any(), any(), any(), any(), any())).thenReturn(0);
        when(reminders.lockDue(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(reminder));
        var schedule = schedule();
        schedule.setStartAt(null);
        when(schedulesRepo.findById(11L)).thenReturn(Optional.of(schedule));

        processor.processDue();

        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.EXPIRED);
        verify(commandJournal, never()).save(any());
        verify(channel, never()).publish(any());
    }

    @Test
    void midnightReminderOnTimedScheduleFails() {
        var reminder = dueReminder(5L, 11L);
        reminder.setKind(ReminderKind.MIDNIGHT);
        when(reminders.returnStuckToPending(any(), any(), any())).thenReturn(0);
        when(reminders.expirePassed(any(), any(), any(), any(), any())).thenReturn(0);
        when(reminders.lockDue(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(reminder));
        when(schedulesRepo.findById(11L)).thenReturn(Optional.of(schedule()));

        processor.processDue();

        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.FAILED);
        verify(commandJournal, never()).save(any());
        verify(channel, never()).publish(any());
    }

    @Test
    void midnightQueriesUseGraceCutoff() {
        when(reminders.returnStuckToPending(any(), any(), any())).thenReturn(0);
        when(reminders.expirePassed(any(), any(), any(), any(), any())).thenReturn(0);
        when(reminders.lockDue(any(), any(), any(), any(), any(Pageable.class))).thenReturn(List.of());

        processor.processDue();

        var cutoff = NOW.minus(MIDNIGHT_GRACE);
        verify(reminders).expirePassed(
                eq(ReminderStatus.EXPIRED), eq(ReminderStatus.PENDING), eq(NOW),
                eq(ReminderKind.MIDNIGHT), eq(cutoff));
        verify(reminders).lockDue(
                eq(ReminderStatus.PENDING), eq(NOW), eq(ReminderKind.MIDNIGHT),
                eq(cutoff), any(Pageable.class));
    }

    private static Reminder dueReminder(long id, long scheduleId) {
        var reminder = new Reminder();
        reminder.setId(id);
        reminder.setScheduleId(scheduleId);
        reminder.setKind(enerjy.musicxService.user.ReminderKind.T_MINUS_3H);
        reminder.setRemindAt(NOW.minusSeconds(300));
        reminder.setStatus(ReminderStatus.PENDING);
        reminder.setAttempts(0);
        return reminder;
    }

    private static userSchedules schedule() {
        var schedule = new userSchedules();
        schedule.setSceduleId(11L);
        schedule.setUserId(7L);
        schedule.setTitle("Урок гитары");
        schedule.setStartAt(NOW.plus(Duration.ofHours(2)));
        schedule.setTimezone("Europe/Moscow");
        schedule.setStatus(enerjy.musicxService.user.ScheduleStatus.ACTIVE);
        schedule.setSourceChannel(Channels.TELEGRAM);
        schedule.setSourceConversationId("42");
        return schedule;
    }

    private static TransactionTemplate stubTransactions() {
        PlatformTransactionManager manager = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
        return new TransactionTemplate(manager);
    }
}
