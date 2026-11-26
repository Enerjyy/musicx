package enerjy.musicxService.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import enerjy.musicxService.Kafka.CFG.ContractKafkaConfig;
import enerjy.musicxService.messaging.contract.DeliveryResult;
import enerjy.musicxService.messaging.contract.Topics;
import enerjy.musicxService.user.CommandDeliveryStatus;
import enerjy.musicxService.user.CommandJournalEntry;
import enerjy.musicxService.user.CommandJournalRepo;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Результаты доставки из транспорта.
 *
 * Обновляет журнал команд: SENT/ALREADY_GONE закрывают команду,
 * BLOCKED/FAILED — окончательный отказ, RATE_LIMITED/TRANSIENT_FAILURE
 * оставляют PENDING (повтор возможен — см. CreateScheduleUseCase).
 */
@Component
public class OutboundResultsConsumer {

    private final ObjectMapper objectMapper;
    private final CommandJournalRepo commandJournal;

    public OutboundResultsConsumer(ObjectMapper objectMapper, CommandJournalRepo commandJournal) {
        this.objectMapper = objectMapper;
        this.commandJournal = commandJournal;
    }

    @KafkaListener(topics = Topics.OUTBOUND_RESULTS, containerFactory = ContractKafkaConfig.FACTORY)
    public void handle(String json, Acknowledgment ack) throws JsonProcessingException {
        DeliveryResult result = objectMapper.readValue(json, DeliveryResult.class);
        commandJournal.findById(result.commandId()).ifPresent(entry -> {
            if (applyStatus(result, entry)) {
                commandJournal.save(entry);
            }
        });
        ack.acknowledge();
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
}
