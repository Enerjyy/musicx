package enerjy.musicxService.LlmRequests.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Запрос к OpenRouter.
 *
 * @param models      список запасных моделей: OpenRouter использует их, если
 *                    основная модель недоступна
 * @param temperature 0 — разбор записи должен быть детерминированным
 * @param reasoning   управление reasoning модели; для парсинга он отключён,
 *                    чтобы не тратить время и токены
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatRequest(
        String model,
        List<Message> messages,
        @JsonProperty("response_format") ResponseFormat responseFormat,
        @JsonProperty("max_tokens") Integer maxTokens,
        Provider provider,
        List<String> models,
        Double temperature,
        Reasoning reasoning
) {

    /** Совместимость со старыми вызовами: без запасных моделей, температуры и reasoning. */
    public ChatRequest(
            String model,
            List<Message> messages,
            ResponseFormat responseFormat,
            Integer maxTokens,
            Provider provider
    ) {
        this(model, messages, responseFormat, maxTokens, provider, null, null, null);
    }

    public record Message(String role, String content) {
    }

    public record ResponseFormat(
            String type,
            @JsonProperty("json_schema") JsonSchema jsonSchema
    ) {
    }

    public record JsonSchema(
            String name,
            boolean strict,
            Object schema
    ) {
    }

    public record Provider(
            @JsonProperty("require_parameters") boolean requireParameters
    ) {
    }

    public record Reasoning(
            @JsonProperty("enabled") Boolean enabled
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(
            List<Choice> choices,
            Usage usage
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            Message message,
            @JsonProperty("finish_reason") String finishReason
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens,
            Double cost
    ) {
    }
}
