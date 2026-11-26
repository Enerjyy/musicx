package enerjy.muscxTG.telegram.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import enerjy.muscxTG.messaging.KafkaMessagePublisher;
import enerjy.muscxTG.messaging.contract.Channels;
import enerjy.muscxTG.messaging.contract.DeliveryResult;
import enerjy.muscxTG.messaging.contract.DeliveryStatus;
import enerjy.muscxTG.messaging.contract.DeleteMessage;
import enerjy.muscxTG.messaging.contract.KafkaMessage;
import enerjy.muscxTG.messaging.contract.SendMessage;
import enerjy.muscxTG.messaging.contract.ShowProgress;
import enerjy.muscxTG.telegram.gateway.TelegramApiException;
import enerjy.muscxTG.telegram.gateway.TelegramGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramCommandListenerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Mock
    private TelegramGateway gateway;

    @Mock
    private KafkaMessagePublisher publisher;

    @Mock
    private Acknowledgment ack;

    private TelegramCommandListener listener;

    @BeforeEach
    void setUp() {
        listener = new TelegramCommandListener(mapper, gateway, publisher, Duration.ofSeconds(30), 100);
    }

    @Test
    void sendMessagePublishesSentResult() throws Exception {
        when(gateway.sendMessage(42L, "Готово")).thenReturn(555L);

        listener.handle(json(SendMessage.create(Channels.TELEGRAM, "42", "cmd-1", "Готово")), ack);

        var published = ArgumentCaptor.forClass(KafkaMessage.class);
        verify(publisher).publish(published.capture());
        var result = (DeliveryResult) published.getValue();
        assertThat(result.status()).isEqualTo(DeliveryStatus.SENT);
        assertThat(result.commandId()).isEqualTo("cmd-1");
        assertThat(result.externalMessageId()).isEqualTo("555");
        assertThat(result.conversationId()).isEqualTo("42");
        verify(ack).acknowledge();
    }

    @Test
    void redeliveredSendIsDeduplicated() throws Exception {
        when(gateway.sendMessage(42L, "Готово")).thenReturn(555L);
        String json = json(SendMessage.create(Channels.TELEGRAM, "42", "cmd-1", "Готово"));

        listener.handle(json, ack);
        listener.handle(json, ack);

        verify(gateway, times(1)).sendMessage(42L, "Готово");
        verify(publisher, times(1)).publish(any(KafkaMessage.class));
        verify(ack, times(2)).acknowledge();
    }

    /** Устранимый сбой освобождает commandId: редоставка может повторить команду. */
    @Test
    void transientFailurePublishesResultAndAllowsRetry() throws Exception {
        when(gateway.sendMessage(42L, "Готово"))
                .thenThrow(new TelegramApiException(DeliveryStatus.TRANSIENT_FAILURE, null, "timeout"))
                .thenReturn(555L);
        String json = json(SendMessage.create(Channels.TELEGRAM, "42", "cmd-1", "Готово"));

        listener.handle(json, ack);
        listener.handle(json, ack);

        verify(gateway, times(2)).sendMessage(42L, "Готово");
        var published = ArgumentCaptor.forClass(KafkaMessage.class);
        verify(publisher, times(2)).publish(published.capture());
        assertThat(published.getAllValues())
                .extracting(value -> ((DeliveryResult) value).status())
                .containsExactly(DeliveryStatus.TRANSIENT_FAILURE, DeliveryStatus.SENT);
    }

    /** Окончательный отказ не повторяется: редоставка того же commandId отбрасывается. */
    @Test
    void blockedFailureIsTerminalForDeduplication() throws Exception {
        when(gateway.sendMessage(42L, "Готово"))
                .thenThrow(new TelegramApiException(DeliveryStatus.BLOCKED, null, "blocked"));
        String json = json(SendMessage.create(Channels.TELEGRAM, "42", "cmd-1", "Готово"));

        listener.handle(json, ack);
        listener.handle(json, ack);

        verify(gateway, times(1)).sendMessage(42L, "Готово");
        verify(publisher, times(1)).publish(any(KafkaMessage.class));
    }

    @Test
    void rateLimitedPublishesRetryAfterSec() throws Exception {
        when(gateway.sendMessage(42L, "Готово"))
                .thenThrow(new TelegramApiException(DeliveryStatus.RATE_LIMITED, 17, "slow down"));

        listener.handle(json(SendMessage.create(Channels.TELEGRAM, "42", "cmd-1", "Готово")), ack);

        var result = singleResult();
        assertThat(result.status()).isEqualTo(DeliveryStatus.RATE_LIMITED);
        assertThat(result.retryAfterSec()).isEqualTo(17);
    }

    @Test
    void deleteMessagePublishesSentWithExternalId() throws Exception {
        listener.handle(json(DeleteMessage.create(Channels.TELEGRAM, "42", "cmd-2", "555")), ack);

        verify(gateway).deleteMessage(42L, 555L);
        var result = singleResult();
        assertThat(result.status()).isEqualTo(DeliveryStatus.SENT);
        assertThat(result.externalMessageId()).isEqualTo("555");
    }

    @Test
    void alreadyGonePublishesAlreadyGoneWithExternalId() throws Exception {
        org.mockito.Mockito.doThrow(new TelegramApiException(DeliveryStatus.ALREADY_GONE, null, "not found"))
                .when(gateway).deleteMessage(42L, 555L);

        listener.handle(json(DeleteMessage.create(Channels.TELEGRAM, "42", "cmd-2", "555")), ack);

        var result = singleResult();
        assertThat(result.status()).isEqualTo(DeliveryStatus.ALREADY_GONE);
        assertThat(result.externalMessageId()).isEqualTo("555");
    }

    @Test
    void freshProgressStreamsDraftWithoutResult() throws Exception {
        var progress = ShowProgress.create(Channels.TELEGRAM, "42", "7", "Разбираю запрос…");

        listener.handle(json(progress), ack);

        verify(gateway).sendMessageDraft(42L, 7, "Разбираю запрос…");
        verify(publisher, never()).publish(any(KafkaMessage.class));
        verify(ack).acknowledge();
    }

    @Test
    void staleProgressIsDropped() throws Exception {
        var stale = new ShowProgress(
                UUID.randomUUID().toString(), 1, Instant.now().minusSeconds(60).toString(),
                Channels.TELEGRAM, "42", "7", "Разбираю запрос…");

        listener.handle(json(stale), ack);

        verify(gateway, never()).sendMessageDraft(anyLong(), anyInt(), anyString());
        verify(publisher, never()).publish(any(KafkaMessage.class));
        // Подтверждаем и отбрасываем: повтор не имеет смысла для черновика.
        verify(ack).acknowledge();
    }

    @Test
    void unparseableIdsInProgressAreDroppedWithoutCrash() throws Exception {
        var progress = ShowProgress.create(Channels.TELEGRAM, "not-a-number", "abc", "текст");

        listener.handle(json(progress), ack);

        verify(gateway, never()).sendMessageDraft(anyLong(), anyInt(), anyString());
        verify(publisher, never()).publish(any(KafkaMessage.class));
        verify(ack).acknowledge();
    }

    @Test
    void garbageJsonIsPropagatedAndNotAcked() {
        assertThatThrownBy(() -> listener.handle("{bebebe}", ack))
                .isInstanceOf(JsonProcessingException.class);
        verify(ack, never()).acknowledge();
    }

    private DeliveryResult singleResult() {
        var published = ArgumentCaptor.forClass(KafkaMessage.class);
        verify(publisher).publish(published.capture());
        return (DeliveryResult) published.getValue();
    }

    private String json(KafkaMessage message) throws Exception {
        return mapper.writeValueAsString(message);
    }
}
