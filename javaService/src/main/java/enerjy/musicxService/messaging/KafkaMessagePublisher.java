package enerjy.musicxService.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import enerjy.musicxService.messaging.contract.KafkaMessage;
import enerjy.musicxService.messaging.contract.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/** Публикует сообщения контракта в нужный топик и не теряет ошибки отправки. */
@Component
public class KafkaMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaMessagePublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaMessagePublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<SendResult<String, String>> publish(KafkaMessage message) {
        return publish(Topics.forMessage(message), message);
    }

    public CompletableFuture<SendResult<String, String>> publish(String topic, KafkaMessage message) {
        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize " + message.getClass().getSimpleName(), e);
        }

        return kafkaTemplate.send(topic, message.partitionKey(), json)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.error("publish failed: topic={} eventId={} key={}",
                                topic, message.eventId(), message.partitionKey(), error);
                    } else {
                        log.debug("published: topic={} eventId={} partition={} offset={}",
                                topic, message.eventId(),
                                result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    }
                });
    }
}
