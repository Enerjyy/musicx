package enerjy.muscxTG.TelegramBot.TGAPI.Sending;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import enerjy.muscxTG.TelegramBot.TGAPI.System.WebAPIInstance;
import enerjy.muscxTG.messaging.contract.DeliveryStatus;
import enerjy.muscxTG.telegram.gateway.TelegramApiException;
import enerjy.muscxTG.telegram.gateway.TelegramGateway;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Реализация TelegramGateway поверх HTTP Bot API.
 *
 * Маппинг ошибок в статусы контракта:
 * <ul>
 *   <li>403 → BLOCKED (пользователь заблокировал бота);</li>
 *   <li>429 → RATE_LIMITED с retry_after из parameters;</li>
 *   <li>400 «message to delete not found» → ALREADY_GONE;</li>
 *   <li>прочие 4xx → FAILED (запрос не исправится сам);</li>
 *   <li>5xx и сетевые сбои → TRANSIENT_FAILURE.</li>
 * </ul>
 */
@Service
public class SendAPI implements TelegramGateway {

    private final WebAPIInstance webAPIInstance;
    private final org.springframework.web.client.RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public SendAPI(WebAPIInstance webAPIInstance,
                   org.springframework.web.client.RestTemplate restTemplate,
                   ObjectMapper objectMapper) {
        this.webAPIInstance = webAPIInstance;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void sendMessageDraft(long chatId, int draftId, String text) {
        post("sendMessageDraft", Map.of(
                "chat_id", chatId,
                "draft_id", draftId,
                "text", text));
    }

    @Override
    public long sendMessage(long chatId, String text) {
        String body = post("sendMessage", Map.of(
                "chat_id", chatId,
                "text", text));
        return extractMessageId(body);
    }

    @Override
    public void deleteMessage(long chatId, long messageId) {
        post("deleteMessage", Map.of(
                "chat_id", chatId,
                "message_id", messageId));
    }

    private String post(String method, Map<String, Object> payload) {
        String url = webAPIInstance.getApiUrl() + method;
        String body;
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, payload, String.class);
            body = response.getBody();
        } catch (HttpStatusCodeException e) {
            throw mapFailure(method, e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            throw new TelegramApiException(DeliveryStatus.TRANSIENT_FAILURE, null,
                    method + " failed: " + e.getMessage());
        }
        requireOk(method, body);
        return body;
    }

    private void requireOk(String method, String body) {
        TelegramResponse response = parse(method, body);
        if (Boolean.TRUE.equals(response.ok())) {
            return;
        }
        int code = response.errorCode() != null ? response.errorCode() : 0;
        throw mapFailure(method, code, response.description(), response);
    }

    private TelegramApiException mapFailure(String method, int statusCode, String body) {
        TelegramResponse response = parse(method, body);
        int code = response.errorCode() != null ? response.errorCode() : statusCode;
        return mapFailure(method, code, response.description(), response);
    }

    private TelegramApiException mapFailure(String method, int code, String description, TelegramResponse response) {
        String details = method + " HTTP " + code + ": " + description;
        if (code == 403) {
            return new TelegramApiException(DeliveryStatus.BLOCKED, null, details);
        }
        if (code == 429) {
            return new TelegramApiException(DeliveryStatus.RATE_LIMITED, retryAfter(response), details);
        }
        if (code == 400 && description != null && description.contains("message to delete not found")) {
            return new TelegramApiException(DeliveryStatus.ALREADY_GONE, null, details);
        }
        return new TelegramApiException(
                code >= 500 ? DeliveryStatus.TRANSIENT_FAILURE : DeliveryStatus.FAILED,
                null,
                details);
    }

    private static Integer retryAfter(TelegramResponse response) {
        if (response.parameters() != null && response.parameters().retryAfter() != null) {
            return response.parameters().retryAfter();
        }
        return null;
    }

    private long extractMessageId(String body) {
        TelegramResponse response = parse("sendMessage", body);
        if (response.result() == null || response.result().messageId() == null) {
            throw new TelegramApiException(DeliveryStatus.FAILED, null,
                    "sendMessage: no message_id in response: " + body);
        }
        return response.result().messageId();
    }

    private TelegramResponse parse(String method, String body) {
        try {
            return objectMapper.readValue(body, TelegramResponse.class);
        } catch (Exception e) {
            throw new TelegramApiException(DeliveryStatus.TRANSIENT_FAILURE, null,
                    method + ": unparseable response: " + body, e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TelegramResponse(
            Boolean ok,
            Result result,
            @JsonProperty("error_code") Integer errorCode,
            String description,
            Parameters parameters
    ) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Result(@JsonProperty("message_id") Long messageId) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Parameters(@JsonProperty("retry_after") Integer retryAfter) {
        }
    }
}
