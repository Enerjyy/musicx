package enerjy.musicxService.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import enerjy.musicxService.messaging.contract.Channels;
import enerjy.musicxService.messaging.contract.DeleteMessage;
import enerjy.musicxService.messaging.contract.IncomingMessage;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaMessagePublisherTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> template = mock(KafkaTemplate.class);

    private final KafkaMessagePublisher publisher = new KafkaMessagePublisher(template, mapper);

    @Test
    void publishesToTopicByTypeWithConversationKey() throws Exception {
        when(template.send(anyString(), anyString(), anyString())).thenReturn(sent("inbound.messages"));
        var message = IncomingMessage.create(Channels.TELEGRAM, "777", "10", "5", "привет", "ru", "9");

        publisher.publish(message).get();

        var topic = ArgumentCaptor.forClass(String.class);
        var key = ArgumentCaptor.forClass(String.class);
        var value = ArgumentCaptor.forClass(String.class);
        verify(template).send(topic.capture(), key.capture(), value.capture());
        assertThat(topic.getValue()).isEqualTo("inbound.messages");
        assertThat(key.getValue()).isEqualTo("TELEGRAM:777");
        assertThat(mapper.readTree(value.getValue()).get("type").asText()).isEqualTo("INCOMING_MESSAGE");
        assertThat(mapper.readTree(value.getValue()).get("eventId").asText()).isEqualTo(message.eventId());
    }

    @Test
    void commandsGoToChannelTopic() throws Exception {
        when(template.send(anyString(), anyString(), anyString())).thenReturn(sent("outbound.commands.telegram"));

        publisher.publish(DeleteMessage.create(Channels.TELEGRAM, "1", "cmd", "55")).get();

        verify(template).send(org.mockito.ArgumentMatchers.eq("outbound.commands.telegram"),
                org.mockito.ArgumentMatchers.eq("TELEGRAM:1"), anyString());
    }

    @Test
    void sendFailureIsPropagatedToTheCaller() {
        var failed = new CompletableFuture<SendResult<String, String>>();
        failed.completeExceptionally(new IllegalStateException("broker down"));
        when(template.send(anyString(), anyString(), anyString())).thenReturn(failed);

        var future = publisher.publish(DeleteMessage.create(Channels.TELEGRAM, "1", "cmd", "55"));

        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .hasRootCauseMessage("broker down");
    }

    private static CompletableFuture<SendResult<String, String>> sent(String topic) {
        var record = new ProducerRecord<String, String>(topic, "k", "v");
        var metadata = new RecordMetadata(new TopicPartition(topic, 0), 0, 0, 0, 0, 0);
        return CompletableFuture.completedFuture(new SendResult<>(record, metadata));
    }
}
