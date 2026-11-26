package enerjy.musicxService.LlmRequests.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesFallbackModelsTemperatureAndReasoning() throws Exception {
        var request = new ChatRequest(
                "deepseek/deepseek-v4.1-flash",
                List.of(new ChatRequest.Message("user", "завтра в 15:00")),
                null,
                1024,
                new ChatRequest.Provider(true),
                List.of("qwen/qwen3.8-flash"),
                0.0,
                new ChatRequest.Reasoning(false)
        );

        var json = mapper.readTree(mapper.writeValueAsString(request));

        assertThat(json.get("model").asText()).isEqualTo("deepseek/deepseek-v4.1-flash");
        assertThat(json.get("models").toString()).isEqualTo("[\"qwen/qwen3.8-flash\"]");
        assertThat(json.get("temperature").asDouble()).isZero();
        assertThat(json.get("reasoning").get("enabled").asBoolean()).isFalse();
        assertThat(json.get("provider").get("require_parameters").asBoolean()).isTrue();
        assertThat(json.get("max_tokens").asInt()).isEqualTo(1024);
    }

    @Test
    void legacyConstructorKeepsWireFormat() throws Exception {
        var request = new ChatRequest(
                "model",
                List.of(new ChatRequest.Message("user", "text")),
                new ChatRequest.ResponseFormat("json_schema", null),
                1024,
                new ChatRequest.Provider(true)
        );

        var json = mapper.readTree(mapper.writeValueAsString(request));

        assertThat(json.has("response_format")).isTrue();
        // Новые поля не отправляются, если они не заданы.
        assertThat(json.has("models")).isFalse();
        assertThat(json.has("temperature")).isFalse();
        assertThat(json.has("reasoning")).isFalse();
    }
}
