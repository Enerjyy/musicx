package enerjy.muscxTG.Kafka.Listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import enerjy.muscxTG.Kafka.DTO.TelegramInteractionEvent;
import enerjy.muscxTG.TelegramBot.TGAPI.Sending.SendAPI;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TGEventListener {
    private final SendAPI sendAPI;
    private final ObjectMapper objectMapper;

    public TGEventListener(SendAPI sendAPI, ObjectMapper objectMapper) {
        this.sendAPI = sendAPI;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "telegram-interactions", groupId = "telegram-bot-group-latest1")
    public void handleInteraction(String json) {
        try {
            TelegramInteractionEvent event;
            try {
                event = objectMapper.readValue(json, TelegramInteractionEvent.class);
            } catch (Exception e) {
                String innerJson = objectMapper.readValue(json, String.class);
                event = objectMapper.readValue(innerJson, TelegramInteractionEvent.class);
            }

            System.out.println("Message received successfully: " + event.getText());
            sendAPI.sendMessage(event.getChatId(), event.getText());

        } catch (Exception e) {
            System.err.println("Failed to parse payload: " + json);
            e.printStackTrace();
        }
    }
}
