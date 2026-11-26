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
 *   <li>REMINDER — обрабатывается планировщиком напоминаний (Шаг 8).</li>
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
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final Duration deletionDelay;
    private final Duration deletionRetryDelay;

    @Autowired
    public OutboundResultsConsumer(
            ObjectMapper objectMapper,
            CommandJournalRepo commandJournal,
            ScheduledDeletionRepo deletions,
            TransactionTemplate transactions,
            @Value("${app.deletions.delay:PT30S}") Duration deletionDelay,
            @Value("${app.deletions.retry-delay:PT60S}") Duration deletionRetryDelay
    ) {
        this(objectMapper, commandJournal, deletions, transactions,
                Clock.systemUTC(), deletionDelay, deletionRetryDelay);
    }

    OutboundResultsConsumer(
            ObjectMapper objectMapper,
            CommandJournalRepo commandJournal,
            ScheduledDeletionRepo deletions,
            TransactionTemplate transactions,
            Clock clock,
            Duration deletionDelay,
            Duration deletionRetryDelay
    ) {
        this.objectMapper = objectMapper;
        this.commandJournal = commandJournal;
        this.deletions = deletions;
        this.transactions = transactions;
        this.clock = clock;
        this.deletionDelay = deletionDelay;
        this.deletionRetryDelay = deletionRetryDelay;
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
            case REMINDER -> {
                // Планировщик напоминаний (Шаг 8) закрывает свой статус.
            }
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

    private Duration retryDelay(DeliveryResult result) {
        return result.retryAfterSec() != null && result.retryAfterSec() > 0
                ? Duration.ofSeconds(result.retryAfterSec())
                : deletionRetryDelay;
    }
}
