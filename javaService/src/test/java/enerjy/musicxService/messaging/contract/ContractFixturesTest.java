package enerjy.musicxService.messaging.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContractFixturesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static Stream<Arguments> fixtures() {
        return Stream.of(
                Arguments.of("incoming-message.json", IncomingMessage.class),
                Arguments.of("show-progress.json", ShowProgress.class),
                Arguments.of("send-message.json", SendMessage.class),
                Arguments.of("delete-message.json", DeleteMessage.class),
                Arguments.of("delivery-result.json", DeliveryResult.class)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void fixtureRoundTripsWithoutChanges(String file, Class<?> expectedType) throws Exception {
        String json = fixture(file);

        KafkaMessage message = MAPPER.readValue(json, KafkaMessage.class);

        assertThat(message).isInstanceOf(expectedType);
        assertThat(message.schemaVersion()).isEqualTo(KafkaMessage.CURRENT_SCHEMA_VERSION);
        JsonNode written = MAPPER.readTree(MAPPER.writeValueAsString(message));
        assertThat(written).isEqualTo(MAPPER.readTree(json));
    }

    @Test
    void incomingMessageFieldsAreMapped() throws Exception {
        var message = MAPPER.readValue(fixture("incoming-message.json"), IncomingMessage.class);

        assertThat(message.eventId()).isEqualTo("11111111-1111-4111-8111-111111111111");
        assertThat(message.channel()).isEqualTo(Channels.TELEGRAM);
        assertThat(message.conversationId()).isEqualTo("123456789");
        assertThat(message.messageId()).isEqualTo("42");
        assertThat(message.userRef()).isEqualTo("123456789");
        assertThat(message.text()).isEqualTo("завтра в 15:00 урок гитары на час");
        assertThat(message.locale()).isEqualTo("ru");
        assertThat(message.progressRef()).isEqualTo("987654321");
        assertThat(message.partitionKey()).isEqualTo("TELEGRAM:123456789");
    }

    @Test
    void outboundCommandIsDeserializedToItsSubtype() throws Exception {
        OutboundCommand progress = MAPPER.readValue(fixture("show-progress.json"), OutboundCommand.class);
        OutboundCommand send = MAPPER.readValue(fixture("send-message.json"), OutboundCommand.class);
        OutboundCommand delete = MAPPER.readValue(fixture("delete-message.json"), OutboundCommand.class);

        assertThat(progress).isInstanceOf(ShowProgress.class);
        assertThat(send).isInstanceOf(SendMessage.class);
        assertThat(delete).isInstanceOf(DeleteMessage.class);
    }

    @Test
    void nonCommandTypeIsRejectedAsOutboundCommand() {
        assertThatThrownBy(() -> MAPPER.readValue(fixture("incoming-message.json"), OutboundCommand.class))
                .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    }

    @Test
    void optionalFieldsAreOmittedWhenNull() throws Exception {
        var result = DeliveryResult.create(Channels.TELEGRAM, "1", "cmd-1", DeliveryStatus.BLOCKED, null, null, null);

        JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(result));

        assertThat(json.has("externalMessageId")).isFalse();
        assertThat(json.has("retryAfterSec")).isFalse();
        assertThat(json.has("error")).isFalse();
        assertThat(json.get("status").asText()).isEqualTo("BLOCKED");
    }

    @Test
    void unknownFieldsAreIgnoredForForwardCompatibility() throws Exception {
        ObjectNode json = (ObjectNode) MAPPER.readTree(fixture("send-message.json"));
        json.put("addedInFutureVersion", "x");

        var message = MAPPER.readValue(json.toString(), KafkaMessage.class);

        assertThat(message).isInstanceOf(SendMessage.class);
    }

    @Test
    void missingEnvelopeFieldIsRejected() throws Exception {
        ObjectNode json = (ObjectNode) MAPPER.readTree(fixture("send-message.json"));
        json.remove("conversationId");

        assertThatThrownBy(() -> MAPPER.readValue(json.toString(), KafkaMessage.class))
                .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class)
                .hasMessageContaining("conversationId");
    }

    @Test
    void unknownTypeIsRejected() throws Exception {
        ObjectNode json = (ObjectNode) MAPPER.readTree(fixture("send-message.json"));
        json.put("type", "SOMETHING_ELSE");

        assertThatThrownBy(() -> MAPPER.readValue(json.toString(), KafkaMessage.class))
                .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    }

    @Test
    void topicsAreRoutedByMessageType() throws Exception {
        assertThat(Topics.forMessage(MAPPER.readValue(fixture("incoming-message.json"), KafkaMessage.class)))
                .isEqualTo("inbound.messages");
        assertThat(Topics.forMessage(MAPPER.readValue(fixture("delivery-result.json"), KafkaMessage.class)))
                .isEqualTo("outbound.results");
        assertThat(Topics.forMessage(MAPPER.readValue(fixture("show-progress.json"), KafkaMessage.class)))
                .isEqualTo("outbound.commands.telegram");
        assertThat(Topics.forMessage(MAPPER.readValue(fixture("delete-message.json"), KafkaMessage.class)))
                .isEqualTo("outbound.commands.telegram");
    }

    @Test
    void contractTopicsIncludeDeadLetterTopics() {
        assertThat(Topics.contractTopics()).containsExactlyInAnyOrder(
                "inbound.messages", "inbound.messages.dlt",
                "outbound.results", "outbound.results.dlt",
                "outbound.commands.telegram", "outbound.commands.telegram.dlt");
    }

    @Test
    void factoriesFillEnvelope() {
        var message = IncomingMessage.create(Channels.TELEGRAM, "1", "10", "2", "hi", null, "5");

        assertThat(message.eventId()).isNotBlank();
        assertThat(message.schemaVersion()).isEqualTo(1);
        assertThat(java.time.Instant.parse(message.occurredAt())).isNotNull();
    }

    private static String fixture(String name) throws Exception {
        try (InputStream in = ContractFixturesTest.class.getResourceAsStream("/contracts/v1/" + name)) {
            assertThat(in).as("fixture %s", name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
