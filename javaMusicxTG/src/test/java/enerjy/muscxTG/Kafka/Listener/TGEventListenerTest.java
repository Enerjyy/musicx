package enerjy.muscxTG.Kafka.Listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import enerjy.muscxTG.TelegramBot.TGAPI.Sending.SendAPI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TGEventListenerTest {
    @Mock
    private SendAPI sendAPI;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private TGEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new TGEventListener(sendAPI, objectMapper);
    }

    @Test
    @DisplayName("Calling method check. Checks if after parsing json, sendMessage was called properly jus 1 time")
    void shouldHandleJSON() {
        String json = "{\"chatId\":123,\"text\":\"123\"}";
        listener.handleInteraction(json);
        verify(sendAPI, times(1)).sendMessage(123L, "123");
    }

    @Test
    @DisplayName("Not calling method check. Checks if it doesn't call json as its invalid")
    void shouldNotHandleJson() {
        String invalidJson = "{bebebe}";
        listener.handleInteraction(invalidJson);
        verify(sendAPI, never()).sendMessage(anyLong(), anyString());
    }
}