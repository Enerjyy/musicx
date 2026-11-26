package enerjy.musicxService.messaging;

import enerjy.musicxService.messaging.channel.ChannelRegistry;
import enerjy.musicxService.messaging.channel.OutboundChannel;
import enerjy.musicxService.messaging.contract.SendMessage;
import enerjy.musicxService.user.CommandDeliveryStatus;
import enerjy.musicxService.user.CommandJournalEntry;
import enerjy.musicxService.user.CommandJournalRepo;
import enerjy.musicxService.user.CommandPurpose;
import enerjy.musicxService.user.Reminder;
import enerjy.musicxService.user.ReminderRepo;
import enerjy.musicxService.user.ReminderStatus;
import enerjy.musicxService.user.userSchedules;
import enerjy.musicxService.user.userSchedulesRepo;
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
 * Планировщик напоминаний.
 *
 * Каждый тик в одной транзакции:
 * <ol>
 *   <li>зависшие в SENDING старше {@code app.reminders.stuck-after} — в PENDING;</li>
 *   <li>события, которые уже начались, — в EXPIRED;</li>
 *   <li>порция due-напоминаний (remind_at <= now, start_at > now) захватывается
 *       под строковыми блокировками (PESSIMISTIC_WRITE + SKIP LOCKED) и
 *       переводится в SENDING;</li>
 *   <li>команды отправки публикуются строго после коммита в тот же канал и
 *       диалог, что и у исходной записи (source_channel, source_conversation_id).</li>
 * </ol>
 *
 * Итог статуса определяет OutboundResultsConsumer по DeliveryResult
 * (purpose REMINDER); идемпотентность — уникальный ключ (schedule_id, kind).
 */
@Component
public class ReminderProcessor {

    private static final Logger log = LoggerFactory.getLogger(ReminderProcessor.class);

    private final ReminderRepo reminders;
    private final userSchedulesRepo schedulesRepo;
    private final CommandJournalRepo commandJournal;
    private final ChannelRegistry channels;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final Duration stuckAfter;
    private final Duration reminderOffset;
    private final int batchSize;

    @Autowired
    public ReminderProcessor(
            ReminderRepo reminders,
            userSchedulesRepo schedulesRepo,
            CommandJournalRepo commandJournal,
            ChannelRegistry channels,
            TransactionTemplate transactions,
            @Value("${app.reminders.stuck-after:PT10M}") Duration stuckAfter,
            @Value("${app.reminders.offset:PT3H}") Duration reminderOffset,
            @Value("${app.reminders.batch-size:50}") int batchSize
    ) {
        this(reminders, schedulesRepo, commandJournal, channels, transactions,
                Clock.systemUTC(), stuckAfter, reminderOffset, batchSize);
    }

    ReminderProcessor(
            ReminderRepo reminders,
            userSchedulesRepo schedulesRepo,
            CommandJournalRepo commandJournal,
            ChannelRegistry channels,
            TransactionTemplate transactions,
            Clock clock,
            Duration stuckAfter,
            Duration reminderOffset,
            int batchSize
    ) {
        if (stuckAfter.isNegative() || stuckAfter.isZero()) {
            throw new IllegalArgumentException("stuck-after must be positive");
        }
        if (reminderOffset.isNegative()) {
            throw new IllegalArgumentException("reminder offset must not be negative");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batch-size must be positive");
        }
        this.reminders = reminders;
        this.schedulesRepo = schedulesRepo;
        this.commandJournal = commandJournal;
        this.channels = channels;
        this.transactions = transactions;
        this.clock = clock;
        this.stuckAfter = stuckAfter;
        this.reminderOffset = reminderOffset;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${app.reminders.cron:0 0 * * * *}")
    public void processDue() {
        Instant now = Instant.now(clock);
        transactions.executeWithoutResult(status -> {
            int rescued = reminders.returnStuckToPending(
                    ReminderStatus.PENDING, ReminderStatus.SENDING, now.minus(stuckAfter));
            if (rescued > 0) {
                log.info("returned stuck reminders to pending: count={}", rescued);
            }
            int expired = reminders.expirePassed(ReminderStatus.EXPIRED, ReminderStatus.PENDING, now);
            if (expired > 0) {
                log.info("expired reminders whose events already started: count={}", expired);
            }

            var due = reminders.lockDue(
                    ReminderStatus.PENDING, now, PageRequest.of(0, batchSize, Sort.by("remindAt")));
            for (Reminder reminder : due) {
                claim(reminder, now);
            }
        });
    }

    private void claim(Reminder reminder, Instant now) {
        reminder.setStatus(ReminderStatus.SENDING);
        reminder.setClaimedAt(now);
        reminder.setAttempts(reminder.getAttempts() + 1);

        var schedule = schedulesRepo.findById(reminder.getScheduleId()).orElse(null);
        if (schedule == null || schedule.getSourceChannel() == null || schedule.getStartAt() == null) {
            log.warn("reminder has no usable schedule, failing: reminderId={}, scheduleId={}",
                    reminder.getId(), reminder.getScheduleId());
            reminder.setStatus(ReminderStatus.EXPIRED);
            return;
        }

        OutboundChannel channel;
        try {
            channel = channels.by(schedule.getSourceChannel());
        } catch (IllegalArgumentException e) {
            log.error("reminder has unsupported channel: reminderId={}, channel={}",
                    reminder.getId(), schedule.getSourceChannel());
            reminder.setStatus(ReminderStatus.FAILED);
            return;
        }

        var commandId = UUID.randomUUID().toString();
        commandJournal.save(reminderCommand(commandId, schedule, reminder, now));

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                channel.publish(SendMessage.create(
                        schedule.getSourceChannel(),
                        schedule.getSourceConversationId(),
                        commandId,
                        MessageTemplates.reminder(schedule, reminderOffset)));
            }
        });
    }

    private CommandJournalEntry reminderCommand(
            String commandId,
            userSchedules schedule,
            Reminder reminder,
            Instant now
    ) {
        var entry = new CommandJournalEntry();
        entry.setCommandId(commandId);
        entry.setType(SendMessage.TYPE);
        entry.setChannel(schedule.getSourceChannel());
        entry.setConversationId(schedule.getSourceConversationId());
        entry.setPurpose(CommandPurpose.REMINDER);
        entry.setRelatedId(reminder.getId());
        entry.setStatus(CommandDeliveryStatus.PENDING);
        entry.setCreatedAt(now);
        return entry;
    }
}
