package enerjy.musicxService.messaging;

import enerjy.musicxService.messaging.contract.Channels;
import enerjy.musicxService.messaging.contract.DeleteMessage;
import enerjy.musicxService.messaging.contract.OutboundCommand;
import enerjy.musicxService.messaging.channel.ChannelRegistry;
import enerjy.musicxService.messaging.channel.OutboundChannel;
import enerjy.musicxService.user.CommandDeliveryStatus;
import enerjy.musicxService.user.CommandJournalEntry;
import enerjy.musicxService.user.CommandJournalRepo;
import enerjy.musicxService.user.CommandPurpose;
import enerjy.musicxService.user.DeletionStatus;
import enerjy.musicxService.user.ScheduledDeletion;
import enerjy.musicxService.user.ScheduledDeletionRepo;
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
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledDeletionProcessorTest {

    /** 26.11.2026 10:00 UTC. */
    private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");
    private static final Duration STUCK_AFTER = Duration.ofMinutes(10);

    @Mock
    private ScheduledDeletionRepo deletions;
    @Mock
    private CommandJournalRepo commandJournal;
    @Mock
    private OutboundChannel channel;

    private ScheduledDeletionProcessor processor;

    @BeforeEach
    void setUp() {
        when(channel.id()).thenReturn(Channels.TELEGRAM);
        processor = new ScheduledDeletionProcessor(
                deletions, commandJournal, new ChannelRegistry(List.of(channel)), stubTransactions(),
                Clock.fixed(NOW, ZoneOffset.UTC), STUCK_AFTER, 50);
    }

    @AfterEach
    void clearSynchronizations() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void claimsDueTaskAndSendsDeleteAfterCommit() {
        var task = dueTask(7L);
        when(deletions.returnStuckToPending(
                DeletionStatus.PENDING, DeletionStatus.SENDING, NOW.minus(STUCK_AFTER))).thenReturn(0);
        when(deletions.findByStatusAndDeleteAtLessThanEqual(
                eq(DeletionStatus.PENDING), eq(NOW), any(Pageable.class))).thenReturn(List.of(task));

        TransactionSynchronizationManager.initSynchronization();
        processor.processDue();

        assertThat(task.getStatus()).isEqualTo(DeletionStatus.SENDING);
        assertThat(task.getAttempts()).isEqualTo(1);
        assertThat(task.getClaimedAt()).isEqualTo(NOW);
        assertThat(task.getCommandId()).isNotNull();

        var savedCommand = ArgumentCaptor.forClass(CommandJournalEntry.class);
        verify(commandJournal).save(savedCommand.capture());
        var command = savedCommand.getValue();
        assertThat(command.getType()).isEqualTo(DeleteMessage.TYPE);
        assertThat(command.getPurpose()).isEqualTo(CommandPurpose.DELETE_CONFIRMATION);
        assertThat(command.getRelatedId()).isEqualTo(7L);
        assertThat(command.getStatus()).isEqualTo(CommandDeliveryStatus.PENDING);
        assertThat(command.getChannel()).isEqualTo(Channels.TELEGRAM);
        assertThat(command.getConversationId()).isEqualTo("42");

        // До коммита команда не публикуется.
        verify(channel, never()).publish(any());

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        var published = ArgumentCaptor.forClass(OutboundCommand.class);
        verify(channel, atLeastOnce()).publish(published.capture());
        var deletion = (DeleteMessage) published.getValue();
        assertThat(deletion.commandId()).isEqualTo(command.getCommandId());
        assertThat(deletion.conversationId()).isEqualTo("42");
        assertThat(deletion.externalMessageId()).isEqualTo("555");
    }

    @Test
    void nothingHappensWithoutDueTasks() {
        when(deletions.returnStuckToPending(any(), any(), any())).thenReturn(0);
        when(deletions.findByStatusAndDeleteAtLessThanEqual(
                any(), any(), any(Pageable.class))).thenReturn(List.of());

        processor.processDue();

        verify(commandJournal, never()).save(any());
        verify(channel, never()).publish(any());
    }

    @Test
    void stuckSendingIsReturnedToPendingWithStuckAfterDeadline() {
        when(deletions.returnStuckToPending(any(), any(), any())).thenReturn(2);
        when(deletions.findByStatusAndDeleteAtLessThanEqual(
                any(), any(), any(Pageable.class))).thenReturn(List.of());

        processor.processDue();

        verify(deletions).returnStuckToPending(
                DeletionStatus.PENDING, DeletionStatus.SENDING, NOW.minus(STUCK_AFTER));
    }

    @Test
    void unsupportedChannelFailsTaskWithoutJournalOrPublish() {
        var task = dueTask(9L);
        task.setChannel("WHATSAPP");
        when(deletions.returnStuckToPending(any(), any(), any())).thenReturn(0);
        when(deletions.findByStatusAndDeleteAtLessThanEqual(
                any(), any(), any(Pageable.class))).thenReturn(List.of(task));

        processor.processDue();

        assertThat(task.getStatus()).isEqualTo(DeletionStatus.FAILED);
        verify(commandJournal, never()).save(any());
        verify(channel, never()).publish(any());
    }

    @Test
    void invalidConfigIsRejected() {
        assertThatThrownBy(() -> new ScheduledDeletionProcessor(
                deletions, commandJournal, null, stubTransactions(),
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ZERO, 50))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScheduledDeletionProcessor(
                deletions, commandJournal, null, stubTransactions(),
                Clock.fixed(NOW, ZoneOffset.UTC), STUCK_AFTER, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ScheduledDeletion dueTask(long id) {
        var task = new ScheduledDeletion();
        task.setId(id);
        task.setChannel(Channels.TELEGRAM);
        task.setConversationId("42");
        task.setExternalMessageId("555");
        task.setDeleteAt(NOW.minusSeconds(10));
        task.setStatus(DeletionStatus.PENDING);
        task.setAttempts(0);
        task.setCreatedAt(NOW.minusSeconds(60));
        return task;
    }

    private static TransactionTemplate stubTransactions() {        PlatformTransactionManager manager = new PlatformTransactionManager() {
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
