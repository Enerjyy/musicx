package enerjy.musicxService.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import enerjy.musicxService.messaging.contract.Channels;
import enerjy.musicxService.messaging.contract.DeliveryResult;
import enerjy.musicxService.messaging.contract.DeliveryStatus;
import enerjy.musicxService.user.CommandDeliveryStatus;
import enerjy.musicxService.user.CommandJournalEntry;
import enerjy.musicxService.user.CommandJournalRepo;
import enerjy.musicxService.user.CommandPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboundResultsConsumerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Mock
    private CommandJournalRepo commandJournal;

    @Mock
    private Acknowledgment ack;

    private OutboundResultsConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OutboundResultsConsumer(new ObjectMapper(), commandJournal);
    }

    @Test
    void sentResultClosesCommandAndStoresExternalMessageId() throws Exception {
        var entry = entry(CommandDeliveryStatus.PENDING);
        when(commandJournal.findById("cmd-1")).thenReturn(Optional.of(entry));

        consumer.handle(json(DeliveryStatus.SENT, "555"), ack);

        assertThat(entry.getStatus()).isEqualTo(CommandDeliveryStatus.SENT);
        assertThat(entry.getExternalMessageId()).isEqualTo("555");
        verify(commandJournal).save(entry);
        verify(ack).acknowledge();
    }

    @Test
    void alreadyGoneAlsoClosesCommand() throws Exception {
        var entry = entry(CommandDeliveryStatus.PENDING);
        when(commandJournal.findById("cmd-1")).thenReturn(Optional.of(entry));

        consumer.handle(json(DeliveryStatus.ALREADY_GONE, null), ack);

        assertThat(entry.getStatus()).isEqualTo(CommandDeliveryStatus.SENT);
        verify(commandJournal).save(entry);
    }

    @Test
    void blockedFailsCommand() throws Exception {
        var entry = entry(CommandDeliveryStatus.PENDING);
        when(commandJournal.findById("cmd-1")).thenReturn(Optional.of(entry));

        consumer.handle(json(DeliveryStatus.BLOCKED, null), ack);

        assertThat(entry.getStatus()).isEqualTo(CommandDeliveryStatus.FAILED);
        verify(commandJournal).save(entry);
    }

    /** Временный сбой оставляет PENDING: повтор подтверждения остаётся возможным. */
    @Test
    void transientFailureKeepsPendingWithoutSave() throws Exception {
        var entry = entry(CommandDeliveryStatus.PENDING);
        when(commandJournal.findById("cmd-1")).thenReturn(Optional.of(entry));

        consumer.handle(json(DeliveryStatus.TRANSIENT_FAILURE, null), ack);

        assertThat(entry.getStatus()).isEqualTo(CommandDeliveryStatus.PENDING);
        verify(commandJournal, never()).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void unknownCommandIsIgnoredButAcked() throws Exception {
        when(commandJournal.findById("cmd-1")).thenReturn(Optional.empty());

        consumer.handle(json(DeliveryStatus.SENT, "555"), ack);

        verify(commandJournal, never()).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void garbageJsonIsPropagatedAndNotAcked() {
        assertThatThrownBy(() -> consumer.handle("{bebebe}", ack))
                .isInstanceOf(JsonProcessingException.class);
        verify(ack, never()).acknowledge();
    }

    private static String json(DeliveryStatus status, String externalMessageId) throws Exception {
        var mapper = new ObjectMapper();
        return mapper.writeValueAsString(DeliveryResult.create(
                Channels.TELEGRAM, "42", "cmd-1", status, externalMessageId, null, null));
    }

    private static CommandJournalEntry entry(CommandDeliveryStatus status) {
        var entry = new CommandJournalEntry();
        entry.setCommandId("cmd-1");
        entry.setType("SEND_MESSAGE");
        entry.setChannel(Channels.TELEGRAM);
        entry.setConversationId("42");
        entry.setPurpose(CommandPurpose.CONFIRM_SCHEDULE);
        entry.setRelatedId(11L);
        entry.setStatus(status);
        entry.setCreatedAt(Instant.parse("2026-11-26T10:00:00Z"));
        return entry;
    }
}
