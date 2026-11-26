package enerjy.musicxService.LlmRequests;

import com.fasterxml.jackson.databind.ObjectMapper;
import enerjy.musicxService.LlmRequests.dto.ChatRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LessonParserPromptTest {

    @Test
    void injectsCurrentDateAndWeekdayIntoSystemPrompt() {
        OpenRouterClient client = mock(OpenRouterClient.class);
        when(client.post(any())).thenReturn(response("""
                {"date":"2026-11-26","time":"15:00","title":"барабаны","note":""}
                """));
        Clock clock = Clock.fixed(Instant.parse("2026-11-25T12:00:00Z"), ZoneOffset.UTC);
        LessonParser parser = new LessonParser(client, new ObjectMapper(), "test/model", clock);

        var result = parser.parse("завтра в 15:00 барабаны");

        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 11, 26));

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(client).post(captor.capture());
        assertThat(captor.getValue().messages().get(0).content())
                .contains("2026-11-25")
                .contains("среда")
                .contains("завтра");
    }

    private static ChatRequest.Response response(String content) {
        return new ChatRequest.Response(
                List.of(new ChatRequest.Choice(
                        new ChatRequest.Message("assistant", content), "stop")),
                null);
    }
}
