package enerjy.musicxService.Kafka.Listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import enerjy.musicxService.Kafka.DTO.MusicxEvent;
import enerjy.musicxService.Logic.schedule.ScheduleException;
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

    @KafkaListener(topics = "musicx-events", groupId = "musicx-service")
    public void handle(String json) {
        MusicxEvent event;
        try {
            event = objectMapper.readValue(json, MusicxEvent.class);
        } catch (Exception e) {
            System.err.println("cannot parse event json: " + e.getMessage());
            return;
        }

        try {
            handleSchedule.handleNewSchedule(event.payload(), event.telegramId());
            System.out.println("schedule saved for telegramId=" + event.telegramId());
        } catch (ScheduleException e) {
            System.err.println("schedule rejected for telegramId="
                    + event.telegramId() + ": " + e.getMessage());
        } catch (Exception e) {
            System.err.println("schedule failed for telegramId="
                    + event.telegramId() + ": " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }
    }
}