package enerjy.muscxTG.Kafka.DTO;

public record MusicxEvent(
        String type,
        Long telegramId,
        String payload
) {
}