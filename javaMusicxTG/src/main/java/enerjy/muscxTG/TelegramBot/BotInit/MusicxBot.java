package enerjy.muscxTG.TelegramBot.BotInit;

import enerjy.muscxTG.Kafka.DTO.MusicxEvent;
import enerjy.muscxTG.Kafka.Producer.MusicxEventProducer;
import enerjy.muscxTG.TelegramBot.TGAPI.Sending.SendAPI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class MusicxBot extends TelegramLongPollingBot {

    private final String botUsername;
    private final String botToken;
    private final MusicxEventProducer producer;
    private final SendAPI sendAPI;

    public MusicxBot(@Value("${bot.name}") String botUsername, @Value("${bot.token}") String botToken, MusicxEventProducer producer,SendAPI sendAPI) {
        super(botToken);
        this.botUsername = botUsername;
        this.botToken = botToken;
        this.producer = producer;
        this.sendAPI = sendAPI;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage()) return;
        producer.send(new MusicxEvent(
                "LESSON_CREATED",
                update.getMessage().getFrom().getId(),
                update.getMessage().getText()
        ));
        sendAPI.sendMessage(update.getMessage().getFrom().getId(), update.getMessage().getText());
    }

}
