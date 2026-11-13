package enerjy.musicxService.Kafka.Producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import enerjy.musicxService.Kafka.DTO.TelegramInteractionEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class TGInteractionProducer {
    private static final String TOPIC = "telegram-interactions";
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public TGInteractionProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendNotification(Long chatId, String text) {
        try {
            TelegramInteractionEvent event = new TelegramInteractionEvent(chatId, text);

            String json = objectMapper.writeValueAsString(event);

            kafkaTemplate.send(TOPIC, json);
            System.out.println("Notif sent alr: " + json);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
