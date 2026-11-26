package enerjy.musicxService.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import enerjy.musicxService.Logic.schedule.CreateScheduleUseCase;
import enerjy.musicxService.messaging.contract.Channels;
import enerjy.musicxService.messaging.contract.IncomingMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InboundMessageConsumerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Mock
    private CreateScheduleUseCase useCase;

    @Mock
    private TimezoneCommandHandler timezoneCommands;

    @Mock
    private Acknowledgment ack;

    private InboundMessageConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new InboundMessageConsumer(new ObjectMapper(), useCase, timezoneCommands);
    }

    @Test
    void deserializesDelegatesAndAcks() throws Exception {
        var message = IncomingMessage.create(
                Channels.TELEGRAM, "42", "1001", "42", "завтра в 15:00", "ru", "draft-1");

        consumer.handle(mapper.writeValueAsString(message), ack);

        var received = ArgumentCaptor.forClass(IncomingMessage.class);
        verify(useCase).handle(received.capture());
        assertThat(received.getValue().eventId()).isEqualTo(message.eventId());
        assertThat(received.getValue().conversationId()).isEqualTo("42");
        assertThat(received.getValue().messageId()).isEqualTo("1001");
        assertThat(received.getValue().userRef()).isEqualTo("42");
        assertThat(received.getValue().progressRef()).isEqualTo("draft-1");
        verify(ack).acknowledge();
    }

    /** Команда /timezone уходит в свой обработчик, а не в парсер расписаний. */
    @Test
    void timezoneCommandGoesToTimezoneHandler() throws Exception {
        var message = IncomingMessage.create(
                Channels.TELEGRAM, "42", "1002", "42", "/timezone Europe/Berlin", "ru", null);

        consumer.handle(mapper.writeValueAsString(message), ack);

        verify(timezoneCommands).handle(any());
        verify(useCase, never()).handle(any());
        verify(ack).acknowledge();
    }

    @Test
    void garbageJsonIsPropagatedAndNotAcked() {
        assertThatThrownBy(() -> consumer.handle("{bebebe}", ack))
                .isInstanceOf(JsonProcessingException.class);
        verify(useCase, never()).handle(any());
        verify(ack, never()).acknowledge();
    }

    /** Неожиданный сбой обработки: ack не ставится, ретраи и DLT делает фабрика контракта. */
    @Test
    void useCaseFailureIsPropagatedWithoutAck() throws Exception {
        var message = IncomingMessage.create(Channels.TELEGRAM, "42", "1001", "42", "текст", null, null);
        doThrow(new IllegalStateException("db down")).when(useCase).handle(any());

        assertThatThrownBy(() -> consumer.handle(mapper.writeValueAsString(message), ack))
                .isInstanceOf(IllegalStateException.class);
        verify(ack, never()).acknowledge();
    }
}
