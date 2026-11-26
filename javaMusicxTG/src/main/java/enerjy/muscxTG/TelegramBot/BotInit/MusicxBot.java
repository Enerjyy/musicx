package enerjy.muscxTG.TelegramBot.BotInit;

import enerjy.muscxTG.messaging.KafkaMessagePublisher;
import enerjy.muscxTG.messaging.contract.Channels;
import enerjy.muscxTG.messaging.contract.IncomingMessage;
import enerjy.muscxTG.telegram.gateway.TelegramApiException;
import enerjy.muscxTG.telegram.gateway.TelegramGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Приём сообщений: бот только транспорт.
 *
 * Личный чат с текстом → пустой черновик «Thinking…» (sendMessageDraft с
 * ненулевым draft_id) → публикация IncomingMessage в inbound.messages.
 * Всё остальное (группы, нет текста) игнорируется; эхо и обработка команд
 * не здесь — их делает ядро через Kafka.
 */
@Component
public class MusicxBot extends TelegramLongPollingBot {

    /** У Telegram личный чат имеет тип "private" (объект Chat в Bot API). */
    static final String PRIVATE_CHAT = "private";

    private static final Logger log = LoggerFactory.getLogger(MusicxBot.class);

    private final String botUsername;
    private final String botToken;
    private final KafkaMessagePublisher publisher;
    private final TelegramGateway gateway;

    public MusicxBot(
            @Value("${bot.name}") String botUsername,
            @Value("${bot.token}") String botToken,
            KafkaMessagePublisher publisher,
            TelegramGateway gateway
    ) {
        super(botToken);
        this.botUsername = botUsername;
        this.botToken = botToken;
        this.publisher = publisher;
        this.gateway = gateway;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update == null || !update.hasMessage()) {
            return;
        }
        var message = update.getMessage();
        if (message == null || !message.hasText()) {
            return;
        }
        if (!PRIVATE_CHAT.equals(message.getChat().getType())) {
            return;
        }

        long chatId = message.getChatId();
        int draftId = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        try {
            // Пустой текст → «Thinking...» у пользователя, пока ядро разбирает запрос.
            gateway.sendMessageDraft(chatId, draftId, "");
        } catch (TelegramApiException e) {
            log.warn("draft placeholder failed: chatId={}, status={}", chatId, e.status());
        }

        publisher.publish(IncomingMessage.create(
                Channels.TELEGRAM,
                String.valueOf(chatId),
                String.valueOf(message.getMessageId()),
                String.valueOf(message.getFrom().getId()),
                message.getText(),
                message.getFrom().getLanguageCode(),
                String.valueOf(draftId)));
    }
}
