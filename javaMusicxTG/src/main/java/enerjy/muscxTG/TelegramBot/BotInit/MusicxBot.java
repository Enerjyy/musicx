package enerjy.muscxTG.TelegramBot.BotInit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class MusicxBot extends TelegramLongPollingBot {

    private final String botUsername;
    private final String botToken;

    public MusicxBot(@Value("${bot.name}") String botUsername, @Value("${bot.token}") String botToken) {
        super(botToken);
        this.botUsername = botUsername;
        this.botToken = botToken;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {

    }

}
