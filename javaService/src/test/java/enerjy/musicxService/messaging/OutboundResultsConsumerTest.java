package enerjy.musicxService.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import enerjy.musicxService.messaging.contract.Channels;
import enerjy.musicxService.messaging.contract.DeliveryResult;
import enerjy.musicxService.messaging.contract.DeliveryStatus;
import enerjy.musicxService.user.CommandDeliveryStatus;
import enerjy.musicxService.user.CommandJournalEntry;
import enerjy.musicxService.user.CommandJournalRepo;
import enerjy.musicxService.user.CommandPurpose;
import enerjy.musicxService.user.DeletionStatus;
import enerjy.musicxService.user.ScheduledDeletion;
import enerjy.musicxService.user.ScheduledDeletionRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboundResultsConsumerTest {

    /** 26.11.2026 10:00 UTC — фиксированные часы для расчёта delete_at. */
    private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");

    private final ObjectMapper mapper = new ObjectMapper();

    @Mock
    private CommandJournalRepo commandJournal;

    @Mock
    private ScheduledDeletionRepo deletions;

    @Mock
    private Acknowledgment ack;

    private OutboundResultsConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OutboundResultsConsumer(
                mapper, commandJournal, deletions, stubTransactions(),
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(30), Duration.ofSeconds(60));
    }

    @Test
    void sentConfirmationClosesCommandAndSchedulesDeletion() throws Exception {
        var entry = entry(CommandPurpose.CONFIRM_SCHEDULE, 11L, CommandDeliveryStatus.PENDING);
        when(commandJournal.findById("cmd-1")).thenReturn(Optional.of(entry));
        when(deletions.existsByChannelAndConversationIdAndExternalMessageId(
                Channels.TELEGRAM, "42", "555")).thenReturn(false);

        consumer.handle(json(DeliveryStatus.SENT, "555", null), ack);

        assertThat(entry.getStatus()).isEqualTo(CommandDeliveryStatus.SENT);
        assertThat(entry.getExternalMessageId()).isEqualTo("555");
        verify(commandJournal).save(entry);

        var savedTask = org.mockito.ArgumentCaptor.forClass(ScheduledDeletion.class);
        verify(deletions).save(savedTask.capture());
        var task = savedTask.getValue();
        assertThat(task.getChannel()).isEqualTo(Channels.TELEGRAM);
        assertThat(task.getConversationId()).isEqualTo("42");
        assertThat(task.getExternalMessageId()).isEqualTo("555");
        assertThat(task.getStatus()).isEqualTo(DeletionStatus.PENDING);
        assertThat(task.getDeleteAt()).isEqualTo(NOW.plus(Duration.ofSeconds(30)));
        assertThat(task.getCreatedAt()).isEqualTo(NOW);
        verify(ack).acknowledge();
    }

    @Test
    void duplicateResultDoesNotCreateSecondTask() throws Exception {
        var entry = entry(CommandPurpose.CONFIRM_SCHEDULE, 11L, CommandDeliveryStatus.PENDING);
        when(commandJournal.findById("cmd-1")).thenReturn(Optional.of(entry));
        when(deletions.existsByChannelAndConversationIdAndExternalMessageId(
                Channels.TELEGRAM, "42", "555")).thenReturn(true);

        consumer.handle(json(DeliveryStatus.SENT, "555", null), ack);

        verify(deletions, never()).save(any(ScheduledDeletion.class));
        verify(ack).acknowledge();
    }

    @Test
    void failedConfirmationDoesNotScheduleDeletion() throws Exception {
        var entry = entry(CommandPurpose.CONFIRM_SCHEDULE, 11L, CommandDeliveryStatus.PENDING);
        when(commandJournal.findById("cmd-1")).thenReturn(Optional.of(entry));

        consumer.handle(json(DeliveryStatus.BLOCKED, null, null), ack);

        assertThat(entry.getStatus()).isEqualTo(CommandDeliveryStatus.FAILED);
        verify(deletions, never()).save(any(ScheduledDeletion.class));
    }

    @Test
    void sentDeletionResultCompletesTask() throws Exception {
        var entry = entry(CommandPurpose.DELETE_CONFIRMATION, 77L, CommandDeliveryStatus.PENDING);
        when(commandJournal.findById("cmd-1")).thenReturn(Optional.of(entry));
        var task = deletionTask(DeletionStatus.SENDING);
        when(deletions.findById(77L)).thenReturn(Optional.of(task));

        consumer.handle(json(DeliveryStatus.SENT, "555", null), ack);

        assertThat(task.getStatus()).isEqualTo(DeletionStatus.DONE);
        verify(deletions).save(task);
    }

    @Test
    void alreadyGoneCompletesTask() throws Exception {
        var entry = entry(CommandPurpose.DELETE_CONFIRMATION, 77L, CommandDeliveryStatus.PENDING);
        when(commandJournal.findById("cmd-1")).thenReturn(Optional.of(entry));
        var task = deletionTask(DeletionStatus.SENDING);
        when(deletions.findById(77L)).thenReturn(Optional.of(task));

        consumer.handle(json(DeliveryStatus.ALREADY_GONE, null, null), ack);

        assertThat(task.getStatus()).isEqualTo(DeletionStatus.DONE);
    }

    @Test
    void transientFailureReschedulesWithDefaultDelay() throws Exception {
        var entry = entry(CommandPurpose.DELETE_CONFIRMATION, 77L, CommandDeliveryStatus.PENDING);
        when(commandJournal.findById("cmd-1")).thenReturn(Optional.of(entry));
        var task = deletionTask(DeletionStatus.SENDING);
        when(deletions.findById(77L)).thenReturn(Optional.of(task));

        consumer.handle(json(DeliveryStatus.TRANSIENT_FAILURE, null, null), ack);

        assertThat(task.getStatus()).isEqualTo(DeletionStatus.PENDING);
        assertThat(task.getDeleteAt()).isEqualTo(NOW.plus(Duration.ofSeconds(60)));
    }

    @Test
    void rateLimitedReschedulesWithRetryAfter() throws Exception {
        var entry = entry(CommandPurpose.DELETE_CONFIRMATION, 77L, CommandDeliveryStatus.PENDING);
        when(commandJournal.findById("cmd-1")).thenReturn(Optional.of(entry));
        var task = deletionTask(DeletionStatus.SENDING);
        when(deletions.findById(77L)).thenReturn(Optional.of(task));

        consumer.handle(json(DeliveryStatus.RATE_LIMITED, null, 17), ack);

        assertThat(task.getStatus()).isEqualTo(DeletionStatus.PENDING);
        assertThat(task.getDeleteAt()).isEqualTo(NOW.plus(Duration.ofSeconds(17)));
    }

    @Test
    void blockedDeletionFailsTask() throws Exception {
        var entry = entry(CommandPurpose.DELETE_CONFIRMATION, 77L, CommandDeliveryStatus.PENDING);
        when(commandJournal.findById("cmd-1")).thenReturn(Optional.of(entry));
        var task = deletionTask(DeletionStatus.SENDING);
        when(deletions.findById(77L)).thenReturn(Optional.of(task));

        consumer.handle(json(DeliveryStatus.BLOCKED, null, null), ack);

        assertThat(task.getStatus()).isEqualTo(DeletionStatus.FAILED);
    }

    /** Напоминания не удаляются: задача удаления не создаётся и не меняется. */
    @Test
    void reminderResultsDoNotTouchDeletions() throws Exception {
        var entry = entry(CommandPurpose.REMINDER, 88L, CommandDeliveryStatus.PENDING);
        when(commandJournal.findById("cmd-1")).thenReturn(Optional.of(entry));

        consumer.handle(json(DeliveryStatus.SENT, "555", null), ack);

        assertThat(entry.getStatus()).isEqualTo(CommandDeliveryStatus.SENT);
        verify(deletions, never()).existsByChannelAndConversationIdAndExternalMessageId(any(), any(), any());
        verify(deletions, never()).findById(any());
        verify(deletions, never()).save(any(ScheduledDeletion.class));
        verify(ack).acknowledge();
    }

    @Test
    void transientFailureOnConfirmationKeepsPendingWithoutSave() throws Exception {
        var entry = entry(CommandPurpose.CONFIRM_SCHEDULE, 11L, CommandDeliveryStatus.PENDING);
        when(commandJournal.findById("cmd-1")).thenReturn(Optional.of(entry));

        consumer.handle(json(DeliveryStatus.TRANSIENT_FAILURE, null, null), ack);

        assertThat(entry.getStatus()).isEqualTo(CommandDeliveryStatus.PENDING);
        verify(commandJournal, never()).save(any(CommandJournalEntry.class));
        verify(deletions, never()).save(any(ScheduledDeletion.class));
        verify(ack).acknowledge();
    }

    @Test
    void unknownCommandIsIgnoredButAcked() throws Exception {
        when(commandJournal.findById("cmd-1")).thenReturn(Optional.empty());

        consumer.handle(json(DeliveryStatus.SENT, "555", null), ack);

        verify(commandJournal, never()).save(any(CommandJournalEntry.class));
        verify(ack).acknowledge();
    }

    @Test
    void garbageJsonIsPropagatedAndNotAcked() {
        assertThatThrownBy(() -> consumer.handle("{bebebe}", ack))
                .isInstanceOf(JsonProcessingException.class);
        verify(ack, never()).acknowledge();
    }

    // --- helpers ---

    private String json(DeliveryStatus status, String externalMessageId, Integer retryAfterSec)
            throws Exception {
        return mapper.writeValueAsString(DeliveryResult.create(
                Channels.TELEGRAM, "42", "cmd-1", status, externalMessageId, retryAfterSec, null));
    }

    private static CommandJournalEntry entry(CommandPurpose purpose, Long relatedId, CommandDeliveryStatus status) {
        var entry = new CommandJournalEntry();
        entry.setCommandId("cmd-1");
        entry.setType("SEND_MESSAGE");
        entry.setChannel(Channels.TELEGRAM);
        entry.setConversationId("42");
        entry.setPurpose(purpose);
        entry.setRelatedId(relatedId);
        entry.setStatus(status);
        entry.setCreatedAt(NOW);
        return entry;
    }

    private static ScheduledDeletion deletionTask(DeletionStatus status) {
        var task = new ScheduledDeletion();
        task.setId(77L);
        task.setChannel(Channels.TELEGRAM);
        task.setConversationId("42");
        task.setExternalMessageId("555");
        task.setDeleteAt(NOW.minusSeconds(5));
        task.setStatus(status);
        task.setAttempts(1);
        task.setCreatedAt(NOW.minusSeconds(35));
        return task;
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
