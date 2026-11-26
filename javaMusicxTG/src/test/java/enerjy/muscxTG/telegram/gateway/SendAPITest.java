package enerjy.muscxTG.telegram.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import enerjy.muscxTG.TelegramBot.TGAPI.Sending.SendAPI;
import enerjy.muscxTG.TelegramBot.TGAPI.System.WebAPIInstance;
import enerjy.muscxTG.messaging.contract.DeliveryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SendAPITest {

    private static final String BASE = "https://api.telegram.org/bot123456:TEST/";

    private final ObjectMapper mapper = new ObjectMapper();
    private final org.springframework.web.client.RestTemplate restTemplate =
            new org.springframework.web.client.RestTemplate();
    private final SendAPI gateway = new SendAPI(new WebAPIInstance(restTemplate, "123456:TEST"), restTemplate, mapper);

    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void sendMessageReturnsMessageId() {
        server.expect(requestTo(BASE + "sendMessage"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.chat_id").value(42))
                .andExpect(jsonPath("$.text").value("Готово"))
                .andRespond(withSuccess(
                        "{\"ok\":true,\"result\":{\"message_id\":555}}", MediaType.APPLICATION_JSON));

        assertThat(gateway.sendMessage(42L, "Готово")).isEqualTo(555L);
        server.verify();
    }

    @Test
    void sendMessageDraftSendsChatIdDraftIdAndText() {
        server.expect(requestTo(BASE + "sendMessageDraft"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.chat_id").value(42))
                .andExpect(jsonPath("$.draft_id").value(7))
                .andExpect(jsonPath("$.text").value(""))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        gateway.sendMessageDraft(42L, 7, "");
        server.verify();
    }

    @Test
    void deleteMessageSendsChatIdAndMessageId() {
        server.expect(requestTo(BASE + "deleteMessage"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.chat_id").value(42))
                .andExpect(jsonPath("$.message_id").value(555))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        gateway.deleteMessage(42L, 555L);
        server.verify();
    }

    @Test
    void forbiddenMapsToBlocked() {
        server.expect(requestTo(BASE + "sendMessage"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .body("{\"ok\":false,\"error_code\":403,\"description\":\"Forbidden: bot was blocked\"}"));

        assertThatThrownBy(() -> gateway.sendMessage(42L, "text"))
                .isInstanceOf(TelegramApiException.class)
                .extracting(e -> ((TelegramApiException) e).status())
                .isEqualTo(DeliveryStatus.BLOCKED);
    }

    @Test
    void tooManyRequestsMapsToRateLimitedWithRetryAfter() {
        server.expect(requestTo(BASE + "sendMessage"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("{\"ok\":false,\"error_code\":429,\"description\":\"Too Many Requests: retry after 17\","
                                + "\"parameters\":{\"retry_after\":17}}"));

        assertThatThrownBy(() -> gateway.sendMessage(42L, "text"))
                .isInstanceOfSatisfying(TelegramApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(DeliveryStatus.RATE_LIMITED);
                    assertThat(e.retryAfterSec()).isEqualTo(17);
                });
    }

    @Test
    void deleteNotFoundMapsToAlreadyGone() {
        server.expect(requestTo(BASE + "deleteMessage"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"ok\":false,\"error_code\":400,"
                                + "\"description\":\"Bad Request: message to delete not found\"}"));

        assertThatThrownBy(() -> gateway.deleteMessage(42L, 555L))
                .isInstanceOfSatisfying(TelegramApiException.class, e ->
                        assertThat(e.status()).isEqualTo(DeliveryStatus.ALREADY_GONE));
    }

    @Test
    void otherBadRequestMapsToFailed() {
        server.expect(requestTo(BASE + "sendMessage"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: chat not found\"}"));

        assertThatThrownBy(() -> gateway.sendMessage(42L, "text"))
                .isInstanceOfSatisfying(TelegramApiException.class, e ->
                        assertThat(e.status()).isEqualTo(DeliveryStatus.FAILED));
    }

    @Test
    void serverErrorMapsToTransient() {
        server.expect(requestTo(BASE + "sendMessage"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .body("{\"ok\":false,\"error_code\":502,\"description\":\"Bad Gateway\"}"));

        assertThatThrownBy(() -> gateway.sendMessage(42L, "text"))
                .isInstanceOfSatisfying(TelegramApiException.class, e ->
                        assertThat(e.status()).isEqualTo(DeliveryStatus.TRANSIENT_FAILURE));
    }

    @Test
    void networkFailureMapsToTransient() {
        server.expect(requestTo(BASE + "sendMessage"))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> gateway.sendMessage(42L, "text"))
                .isInstanceOfSatisfying(TelegramApiException.class, e ->
                        assertThat(e.status()).isEqualTo(DeliveryStatus.TRANSIENT_FAILURE));
    }

    @Test
    void responseWithoutMessageIdMapsToFailed() {
        server.expect(requestTo(BASE + "sendMessage"))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.sendMessage(42L, "text"))
                .isInstanceOfSatisfying(TelegramApiException.class, e ->
                        assertThat(e.status()).isEqualTo(DeliveryStatus.FAILED));
    }
}
