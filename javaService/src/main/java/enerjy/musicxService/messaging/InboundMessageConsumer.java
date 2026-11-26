package enerjy.musicxService.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import enerjy.musicxService.Kafka.CFG.ContractKafkaConfig;
import enerjy.musicxService.Logic.schedule.CreateScheduleUseCase;
import enerjy.musicxService.messaging.contract.IncomingMessage;
import enerjy.musicxService.messaging.contract.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Консьюмер входящих сообщений (контракт v1).
 *
 * Ручной ack: смещение коммитится только после успешной обработки, поэтому
 * сбой не теряет сообщение (retry → DLT настраивает фабрика контракта).
 * Ошибки десериализации и ошибки валидации контракта не ретраятся —
 * они уходят в DLT сразу (см. {@link ContractKafkaConfig}).
 */
@Component
public class InboundMessageConsumer {

    private final ObjectMapper objectMapper;
    private final CreateScheduleUseCase createScheduleUseCase;
    private final TimezoneCommandHandler timezoneCommands;

    public InboundMessageConsumer(
            ObjectMapper objectMapper,
            CreateScheduleUseCase createScheduleUseCase,
            TimezoneCommandHandler timezoneCommands
    ) {
        this.objectMapper = objectMapper;
        this.createScheduleUseCase = createScheduleUseCase;
        this.timezoneCommands = timezoneCommands;
    }

    @KafkaListener(topics = Topics.INBOUND_MESSAGES, containerFactory = ContractKafkaConfig.FACTORY)
    public void handle(String json, Acknowledgment ack) throws JsonProcessingException {
        IncomingMessage message = objectMapper.readValue(json, IncomingMessage.class);
        if (TimezoneCommandHandler.isTimezoneCommand(message.text())) {
            timezoneCommands.handle(message);
        } else {
            createScheduleUseCase.handle(message);
        }
        ack.acknowledge();
    }
}
