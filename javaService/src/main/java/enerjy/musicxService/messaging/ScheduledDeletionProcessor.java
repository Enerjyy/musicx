package enerjy.musicxService.messaging;

import enerjy.musicxService.messaging.contract.Channels;
import enerjy.musicxService.messaging.contract.DeleteMessage;
import enerjy.musicxService.messaging.channel.ChannelRegistry;
import enerjy.musicxService.messaging.channel.OutboundChannel;
import enerjy.musicxService.user.CommandDeliveryStatus;
import enerjy.musicxService.user.CommandJournalEntry;
import enerjy.musicxService.user.CommandJournalRepo;
import enerjy.musicxService.user.CommandPurpose;
import enerjy.musicxService.user.DeletionStatus;
import enerjy.musicxService.user.ScheduledDeletion;
import enerjy.musicxService.user.ScheduledDeletionRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Планировщик отложенных удалений.
 *
 * Каждый тик в одной транзакции:
 * <ol>
 *   <li>зависшие в SENDING (рестарт/сбой между захватом и результатом)
 *       старше {@code app.deletions.stuck-after} возвращаются в PENDING;</li>
 *   <li>очередная порция просроченных PENDING захватывается со строковыми
 *       блокировками (PESSIMISTIC_WRITE + SKIP LOCKED), переводится в SENDING
 *       и получает запись в журнале команд;</li>
 *   <li>команды {@code DeleteMessage} публикуются строго после коммита.</li>
 * </ol>
 *
 * После рестарта ядра просроченные задачи подхватываются на первом же тике:
 * состояние живёт в БД, а не в памяти.
 */
@Component
public class ScheduledDeletionProcessor {

    private static final Logger log = LoggerFactory.getLogger(ScheduledDeletionProcessor.class);

    private final ScheduledDeletionRepo deletions;
    private final CommandJournalRepo commandJournal;
    private final ChannelRegistry channels;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final Duration stuckAfter;
    private final int batchSize;

    @Autowired
    public ScheduledDeletionProcessor(
            ScheduledDeletionRepo deletions,
            CommandJournalRepo commandJournal,
            ChannelRegistry channels,
            TransactionTemplate transactions,
            @Value("${app.deletions.stuck-after:PT10M}") Duration stuckAfter,
            @Value("${app.deletions.batch-size:50}") int batchSize
    ) {
        this(deletions, commandJournal, channels, transactions,
                Clock.systemUTC(), stuckAfter, batchSize);
    }

    ScheduledDeletionProcessor(
            ScheduledDeletionRepo deletions,
            CommandJournalRepo commandJournal,
            ChannelRegistry channels,
            TransactionTemplate transactions,
            Clock clock,
            Duration stuckAfter,
            int batchSize
    ) {
        if (stuckAfter.isNegative() || stuckAfter.isZero()) {
            throw new IllegalArgumentException("stuck-after must be positive");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batch-size must be positive");
        }
        this.deletions = deletions;
        this.commandJournal = commandJournal;
        this.channels = channels;
        this.transactions = transactions;
        this.clock = clock;
        this.stuckAfter = stuckAfter;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.deletions.poll-interval-ms:5000}")
    public void processDue() {
        Instant now = Instant.now(clock);
        transactions.executeWithoutResult(status -> {
            int rescued = deletions.returnStuckToPending(
                    DeletionStatus.PENDING, DeletionStatus.SENDING, now.minus(stuckAfter));
            if (rescued > 0) {
                log.info("returned stuck deletions to pending: count={}", rescued);
            }

            var due = deletions.findByStatusAndDeleteAtLessThanEqual(
                    DeletionStatus.PENDING, now, PageRequest.of(0, batchSize, Sort.by("deleteAt")));
            for (ScheduledDeletion task : due) {
                claim(task, now);
            }
        });
    }

    private void claim(ScheduledDeletion task, Instant now) {
        task.setStatus(DeletionStatus.SENDING);
        task.setClaimedAt(now);
        task.setAttempts(task.getAttempts() + 1);

        OutboundChannel channel;
        try {
            channel = channels.by(task.getChannel());
        } catch (IllegalArgumentException e) {
            log.error("deletion task has unsupported channel, failing: id={}, channel={}",
                    task.getId(), task.getChannel());
            task.setStatus(DeletionStatus.FAILED);
            return;
        }

        var commandId = UUID.randomUUID().toString();
        task.setCommandId(commandId);
        commandJournal.save(deleteCommand(commandId, task, now));

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                channel.publish(DeleteMessage.create(
                        task.getChannel(),
                        task.getConversationId(),
                        commandId,
                        task.getExternalMessageId()));
            }
        });
    }

    private CommandJournalEntry deleteCommand(String commandId, ScheduledDeletion task, Instant now) {
        var entry = new CommandJournalEntry();
        entry.setCommandId(commandId);
        entry.setType(DeleteMessage.TYPE);
        entry.setChannel(task.getChannel());
        entry.setConversationId(task.getConversationId());
        entry.setPurpose(CommandPurpose.DELETE_CONFIRMATION);
        entry.setRelatedId(task.getId());
        entry.setStatus(CommandDeliveryStatus.PENDING);
        entry.setCreatedAt(now);
        return entry;
    }
}
