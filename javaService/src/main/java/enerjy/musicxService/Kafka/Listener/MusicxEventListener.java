package enerjy.musicxService.Kafka.Listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import enerjy.musicxService.Kafka.DTO.MusicxEvent;
import enerjy.musicxService.Logic.schedule.handleSchedule;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class MusicxEventListener {

    private final ObjectMapper objectMapper;
    private final handleSchedule handleSchedule;

    public MusicxEventListener(ObjectMapper objectMapper, handleSchedule handleSchedule) {
        this.objectMapper = objectMapper;
        this.handleSchedule = handleSchedule;
    }

    @KafkaListener(topics = MusicxEvents.TOPIC, groupId = "musicx-service")
    public void handle(String json) {
        try {
            MusicxEvent event = objectMapper.readValue(json, MusicxEvent.class);
            System.out.println("musicx event: " + event);
            handleSchedule.handleNewSchedule(event.payload(), event.telegramId());
        } catch (Exception e) {
            System.err.println("cannot parse musicx event: " + json);
        }
    }
}

final class MusicxEvents {
    static final String TOPIC = "musicx-events";
    private MusicxEvents() {}
}