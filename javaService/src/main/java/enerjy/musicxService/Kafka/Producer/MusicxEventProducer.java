package enerjy.musicxService.Kafka.Producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import enerjy.musicxService.Kafka.DTO.MusicxEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class MusicxEventProducer {

    public static final String TOPIC = "musicx-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public MusicxEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void send(MusicxEvent event) {
        try {
            kafkaTemplate.send(TOPIC, event.telegramId() == null ? null : String.valueOf(event.telegramId()),
                    objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            throw new IllegalStateException("cannot publish musicx event", e);
        }
    }
}