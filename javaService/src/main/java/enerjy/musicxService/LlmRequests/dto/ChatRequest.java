package enerjy.musicxService.LlmRequests.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ChatRequest(
        String model,
        List<Message> messages,
        @JsonProperty("response_format") ResponseFormat responseFormat,
        @JsonProperty("max_tokens") Integer maxTokens,
        Provider provider
) {

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