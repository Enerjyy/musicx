package enerjy.muscxTG.Kafka.DTO;

public class TelegramInteractionEvent {
    private Long chatId;
    private String text;

    public TelegramInteractionEvent() {}

    public TelegramInteractionEvent(Long chatId, String text) {
        this.chatId = chatId;
        this.text = text;
    }

    public Long getChatId() {
        return chatId;
    }
    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public String getText() {
        return text;
    }
    public void setText(String text) {
        this.text = text;
    }
}
