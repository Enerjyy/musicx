package enerjy.musicxService.LlmRequests;

import enerjy.musicxService.LlmRequests.dto.ChatRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

@Component
public class OpenRouterClient {

    private final RestClient http;

    public OpenRouterClient(
            @Value("${openrouter.base-url}") String baseUrl,
            @Value("${openrouter.api-key}") String apiKey
    ) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));

        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public ChatRequest.Response post(ChatRequest request) {
        try {
            return http.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(ChatRequest.Response.class);
        } catch (RestClientException e) {
            throw new LlmRequestException("openrouter call failed", e);
        }
    }
}