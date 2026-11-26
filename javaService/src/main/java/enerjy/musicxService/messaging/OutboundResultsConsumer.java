package enerjy.musicxService.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import enerjy.musicxService.Kafka.CFG.ContractKafkaConfig;
import enerjy.musicxService.messaging.contract.DeliveryResult;
import enerjy.musicxService.messaging.contract.DeliveryStatus;
import enerjy.musicxService.messaging.contract.Topics;
import enerjy.musicxService.user.CommandDeliveryStatus;
import enerjy.musicxService.user.CommandJournalEntry;
import enerjy.musicxService.user.CommandJournalRepo;
import enerjy.musicxService.user.CommandPurpose;
import enerjy.musicxService.user.DeletionStatus;
import enerjy.musicxService.user.Reminder;
import enerjy.musicxService.user.ReminderRepo;
import enerjy.musicxService.user.ReminderStatus;
import enerjy.musicxService.user.ScheduledDeletion;
import enerjy.musicxService.user.ScheduledDeletionRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Результаты доставки из транспорта.
 *
 * Журнал команд: SENT/ALREADY_GONE закрывают команду, BLOCKED/FAILED —
 * окончательный отказ, RATE_LIMITED/TRANSIENT_FAILURE оставляют PENDING.
 *
 * По целям:
 * <ul>
 *   <li>CONFIRM_SCHEDULE + SENT — создаётся задача отложенного удаления
 *       ({@link ScheduledDeletion}) через {@code app.deletions.delay};</li>
 *   <li>DELETE_CONFIRMATION — обновляет задачу удаления: успех/ALREADY_GONE
 *       → DONE, временный сбой → повтор позже, BLOCKED → FAILED;</li>
 *   <li>REMINDER — итог отправки напоминания: SENT/ALREADY_GONE → SENT,
 *       BLOCKED → FAILED_PERMANENT, FAILED → FAILED, временный сбой →
 *       обратно в PENDING до лимита попыток, затем FAILED.</li>
 * </ul>
 *
 * Автоудаление применяется только к подтверждению создания записи:
 * напоминания и сообщения об ошибках остаются в чате.
 */
@Component
public class OutboundResultsConsumer {

    private static final Logger log = LoggerFactory.getLogger(OutboundResultsConsumer.class);

    private final ObjectMapper objectMapper;
    private final CommandJournalRepo commandJournal;
    private final ScheduledDeletionRepo deletions;
    private final ReminderRepo reminders;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final Duration deletionDelay;
    private final Duration deletionRetryDelay;
    private final int reminderMaxAttempts;

    @Autowired
    public OutboundResultsConsumer(
            ObjectMapper objectMapper,
            CommandJournalRepo commandJournal,
            ScheduledDeletionRepo deletions,
            ReminderRepo reminders,
            TransactionTemplate transactions,
            @Value("${app.deletions.delay:PT30S}") Duration deletionDelay,
            @Value("${app.deletions.retry-delay:PT60S}") Duration deletionRetryDelay,
            @Value("${app.reminders.max-attempts:3}") int reminderMaxAttempts
    ) {
        this(objectMapper, commandJournal, deletions, reminders, transactions,
                Clock.systemUTC(), deletionDelay, deletionRetryDelay, reminderMaxAttempts);
    }

    OutboundResultsConsumer(
            ObjectMapper objectMapper,
            CommandJournalRepo commandJournal,
            ScheduledDeletionRepo deletions,
            ReminderRepo reminders,
            TransactionTemplate transactions,
            Clock clock,
            Duration deletionDelay,
            Duration deletionRetryDelay,
            int reminderMaxAttempts
    ) {
        this.objectMapper = objectMapper;
        this.commandJournal = commandJournal;
        this.deletions = deletions;
        this.reminders = reminders;
        this.transactions = transactions;
        this.clock = clock;
        this.deletionDelay = deletionDelay;
        this.deletionRetryDelay = deletionRetryDelay;
        this.reminderMaxAttempts = reminderMaxAttempts;
    }

    @KafkaListener(topics = Topics.OUTBOUND_RESULTS, containerFactory = ContractKafkaConfig.FACTORY)
    public void handle(String json, Acknowledgment ack) throws JsonProcessingException {
        DeliveryResult result = objectMapper.readValue(json, DeliveryResult.class);
        transactions.executeWithoutResult(status -> apply(result));
        ack.acknowledge();
    }

    private void apply(DeliveryResult result) {
        commandJournal.findById(result.commandId()).ifPresent(entry -> {
            if (applyStatus(result, entry)) {
                commandJournal.save(entry);
            }
            handleByPurpose(result, entry);
        });
    }

    private static boolean applyStatus(DeliveryResult result, CommandJournalEntry entry) {
        return switch (result.status()) {
            case SENT, ALREADY_GONE -> {
                entry.setStatus(CommandDeliveryStatus.SENT);
                if (result.externalMessageId() != null) {
                    entry.setExternalMessageId(result.externalMessageId());
                }
                yield true;
            }
            case BLOCKED, FAILED -> {
                entry.setStatus(CommandDeliveryStatus.FAILED);
                yield true;
            }
            case RATE_LIMITED, TRANSIENT_FAILURE -> false;
        };
    }

    private void handleByPurpose(DeliveryResult result, CommandJournalEntry entry) {
        switch (entry.getPurpose()) {
            case CONFIRM_SCHEDULE -> onConfirmationResult(result, entry);
            case DELETE_CONFIRMATION -> onDeletionResult(result, entry);
            case REMINDER -> onReminderResult(result, entry);
        }
    }

    private void onConfirmationResult(DeliveryResult result, CommandJournalEntry entry) {
        // Удаляется только подтверждение создания; прочие purposes задач не создают.
        if (result.status() != DeliveryStatus.SENT) {
            return;
        }
        String externalMessageId = entry.getExternalMessageId();
        if (externalMessageId == null) {
            log.warn("confirmation sent without external message id, commandId={}", entry.getCommandId());
            return;
        }
        if (deletions.existsByChannelAndConversationIdAndExternalMessageId(
                entry.getChannel(), entry.getConversationId(), externalMessageId)) {
            return;
        }
        var task = new ScheduledDeletion();
        task.setChannel(entry.getChannel());
        task.setConversationId(entry.getConversationId());
        task.setExternalMessageId(externalMessageId);
        task.setDeleteAt(Instant.now(clock).plus(deletionDelay));
        task.setStatus(DeletionStatus.PENDING);
        task.setAttempts(0);
        task.setCreatedAt(Instant.now(clock));
        try {
            deletions.save(task);
        } catch (DataIntegrityViolationException e) {
            // Повторный DeliveryResult того же события — задача уже создана.
        }
    }

    private void onDeletionResult(DeliveryResult result, CommandJournalEntry entry) {
        if (entry.getRelatedId() == null) {
            return;
        }
        deletions.findById(entry.getRelatedId()).ifPresent(task -> {
            switch (result.status()) {
                case SENT, ALREADY_GONE -> task.setStatus(DeletionStatus.DONE);
                case RATE_LIMITED, TRANSIENT_FAILURE -> {
                    // Повтор позже: учитываем retry_after канала, если он пришёл.
                    task.setStatus(DeletionStatus.PENDING);
                    task.setDeleteAt(Instant.now(clock).plus(retryDelay(result)));
                }
                case BLOCKED, FAILED -> task.setStatus(DeletionStatus.FAILED);
            }
            deletions.save(task);
        });
    }

    private void onReminderResult(DeliveryResult result, CommandJournalEntry entry) {
        if (entry.getRelatedId() == null) {
            return;
        }
        reminders.findById(entry.getRelatedId()).ifPresent(reminder -> {
            switch (result.status()) {
                case SENT, ALREADY_GONE -> {
                    reminder.setStatus(ReminderStatus.SENT);
                    reminder.setSentAt(Instant.now(clock));
                }
                case BLOCKED -> reminder.setStatus(ReminderStatus.FAILED_PERMANENT);
                case FAILED -> reminder.setStatus(ReminderStatus.FAILED);
                case RATE_LIMITED, TRANSIENT_FAILURE -> {
                    if (reminder.getAttempts() >= reminderMaxAttempts) {
                        reminder.setStatus(ReminderStatus.FAILED);
                    } else {
                        reminder.setStatus(ReminderStatus.PENDING);
                        reminder.setClaimedAt(null);
                    }
                }
            }
            reminders.save(reminder);
        });
    }

    private Duration retryDelay(DeliveryResult result) {
        return result.retryAfterSec() != null && result.retryAfterSec() > 0
                ? Duration.ofSeconds(result.retryAfterSec())
                : deletionRetryDelay;
    }
}
