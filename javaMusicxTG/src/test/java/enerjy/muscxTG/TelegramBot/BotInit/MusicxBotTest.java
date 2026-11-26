package enerjy.muscxTG.TelegramBot.BotInit;

import com.fasterxml.jackson.databind.ObjectMapper;
import enerjy.muscxTG.messaging.KafkaMessagePublisher;
import enerjy.muscxTG.messaging.contract.IncomingMessage;
import enerjy.muscxTG.messaging.contract.KafkaMessage;
import enerjy.muscxTG.telegram.gateway.TelegramApiException;
import enerjy.muscxTG.telegram.gateway.TelegramGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MusicxBotTest {

    private static final long CHAT_ID = 42L;
    private static final String BOT_TOKEN = "123456789:TestTokenForUnitTestsOnly12345";

    @Mock
    private TelegramGateway gateway;

    @Mock
    private KafkaMessagePublisher publisher;

    @Mock
    private User user;

    private MusicxBot bot;

    @BeforeEach
    void setUp() {
        bot = new MusicxBot("test_bot", BOT_TOKEN, publisher, gateway);
    }

    @Test
    void privateChatWithTextSendsDraftPlaceholderAndPublishesIncomingMessage() {
        when(user.getId()).thenReturn(CHAT_ID);
        when(user.getLanguageCode()).thenReturn("ru");

        bot.onUpdateReceived(update(new Chat(CHAT_ID, "private"), 100, user, "завтра в 15:00 урок"));

        var draftId = ArgumentCaptor.forClass(Integer.class);
        verify(gateway).sendMessageDraft(eq(CHAT_ID), draftId.capture(), eq(""));
        assertThat(draftId.getValue()).isNotZero();

        var published = ArgumentCaptor.forClass(KafkaMessage.class);
        verify(publisher).publish(published.capture());
        var message = (IncomingMessage) published.getValue();
        assertThat(message.conversationId()).isEqualTo("42");
        assertThat(message.messageId()).isEqualTo("100");
        assertThat(message.userRef()).isEqualTo("42");
        assertThat(message.text()).isEqualTo("завтра в 15:00 урок");
        assertThat(message.locale()).isEqualTo("ru");
        assertThat(message.progressRef()).isEqualTo(String.valueOf(draftId.getValue()));
        assertThat(message.partitionKey()).isEqualTo("TELEGRAM:42");
    }

    @Test
    void groupChatIsIgnored() {
        bot.onUpdateReceived(update(new Chat(-100L, "supergroup"), 100, user, "привет"));

        verifyNoInteractions(gateway);
        verifyNoInteractions(publisher);
    }

    @Test
    void channelPostIsIgnored() {
        bot.onUpdateReceived(update(new Chat(-1L, "channel"), 100, user, "привет"));

        verifyNoInteractions(gateway);
        verifyNoInteractions(publisher);
    }

    @Test
    void messageWithoutTextIsIgnored() {
        var message = new Message();
        message.setChat(new Chat(CHAT_ID, "private"));
        message.setMessageId(100);
        var update = new Update();
        update.setMessage(message);

        bot.onUpdateReceived(update);

        verifyNoInteractions(gateway);
        verifyNoInteractions(publisher);
    }

    @Test
    void updateWithoutMessageIsIgnored() {
        bot.onUpdateReceived(new Update());

        verifyNoInteractions(gateway);
        verifyNoInteractions(publisher);
    }

    @Test
    void draftFailureDoesNotStopPublishing() {
        when(user.getId()).thenReturn(CHAT_ID);
        when(user.getLanguageCode()).thenReturn("ru");
        org.mockito.Mockito.doThrow(new TelegramApiException(
                        enerjy.muscxTG.messaging.contract.DeliveryStatus.BLOCKED, null, "blocked"))
                .when(gateway).sendMessageDraft(anyLong(), anyInt(), anyString());

        bot.onUpdateReceived(update(new Chat(CHAT_ID, "private"), 100, user, "завтра в 15:00"));

        verify(publisher).publish(any(IncomingMessage.class));
    }

    private static Update update(Chat chat, Integer messageId, User user, String text) {
        var message = new Message();
        message.setChat(chat);
        message.setMessageId(messageId);
        message.setFrom(user);
        message.setText(text);
        var update = new Update();
        update.setMessage(message);
        return update;
    }
}
