package enerjy.musicxService.Logic.schedule.parse;

import com.fasterxml.jackson.databind.ObjectMapper;
import enerjy.musicxService.LlmRequests.LlmRequestException;
import enerjy.musicxService.LlmRequests.OpenRouterClient;
import enerjy.musicxService.LlmRequests.dto.ChatRequest;
import enerjy.musicxService.LlmRequests.prompts.EventPrompts;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LlmEventParserTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OpenRouterClient client = mock(OpenRouterClient.class);

    @Test
    void mapsModelResponseToParsedEvent() {
        when(client.post(any())).thenReturn(response("""
                {"isEvent":true,"title":"Гитара","description":"первый урок",
                 "startLocal":"2026-11-27T15:00","endLocal":null,"durationMinutes":60,
                 "missing":[],"ambiguous":false}
                """));
        var parser = new LlmEventParser(client, MAPPER, "m1", List.of(), fixedClock());

        var event = parser.parse("завтра в 15:00 урок гитары", BERLIN);

        assertThat(event.isEvent()).isTrue();
        assertThat(event.title()).isEqualTo("Гитара");
        assertThat(event.description()).isEqualTo("первый урок");
        assertThat(event.startLocal()).isEqualTo(LocalDateTime.of(2026, 11, 27, 15, 0));
        assertThat(event.endLocal()).isNull();
        assertThat(event.durationMinutes()).isEqualTo(60);
        assertThat(event.missing()).isEmpty();
        assertThat(event.ambiguous()).isFalse();
        assertThat(event.startAt(BERLIN)).isEqualTo(Instant.parse("2026-11-27T14:00:00Z"));
    }

    @Test
    void toleratesSecondsAndBlankFields() {
        when(client.post(any())).thenReturn(response("""
                {"isEvent":false,"title":"","description":null,
                 "startLocal":"2026-11-27T15:00:00","endLocal":"","durationMinutes":null,
                 "missing":["date"],"ambiguous":true}
                """));
        var parser = new LlmEventParser(client, MAPPER, "m1", List.of(), fixedClock());

        var event = parser.parse("что-то", BERLIN);

        assertThat(event.isEvent()).isFalse();
        assertThat(event.title()).isNull();
        assertThat(event.startLocal()).isEqualTo(LocalDateTime.of(2026, 11, 27, 15, 0));
        assertThat(event.endLocal()).isNull();
        assertThat(event.durationMinutes()).isNull();
        assertThat(event.missing()).containsExactly("date");
        assertThat(event.ambiguous()).isTrue();
    }

    @Test
    void filtersUnknownMissingValues() {
        when(client.post(any())).thenReturn(response("""
                {"isEvent":true,"startLocal":null,"missing":["date","time","venue"]}
                """));
        var parser = new LlmEventParser(client, MAPPER, "m1", List.of(), fixedClock());

        assertThat(parser.parse("репетиция", BERLIN).missing()).containsExactlyInAnyOrder("date", "time");
    }

    @Test
    void promptCarriesUserZoneDateWeekdayAndTime() {
        when(client.post(any())).thenReturn(response("""
                {"isEvent":true,"startLocal":"2026-11-27T15:00"}
                """));
        var parser = new LlmEventParser(client, MAPPER, "m1", List.of(), fixedClock());

        parser.parse("завтра в 15:00", BERLIN);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(client).post(captor.capture());
        var system = captor.getValue().messages().get(0).content();
        assertThat(system).contains("Europe/Berlin").contains("2026-11-26").contains("четверг").contains("11:00");
        assertThat(captor.getValue().temperature()).isZero();
        assertThat(captor.getValue().reasoning().enabled()).isFalse();
        assertThat(captor.getValue().responseFormat().jsonSchema().name()).isEqualTo("calendar_event");
        assertThat(captor.getValue().responseFormat().jsonSchema().strict()).isTrue();
    }

    @Test
    void sendsFallbackModelsInRequest() {
        when(client.post(any())).thenReturn(response("""
                {"isEvent":true,"startLocal":"2026-11-27T15:00"}
                """));
        var parser = new LlmEventParser(client, MAPPER, "m1", List.of("m2", "m3"), fixedClock());

        parser.parse("завтра в 15:00", BERLIN);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(client).post(captor.capture());
        assertThat(captor.getValue().model()).isEqualTo("m1");
        assertThat(captor.getValue().models()).containsExactly("m2", "m3");
    }

    @Test
    void fallsBackToNextModelWhenPreviousFails() {
        when(client.post(any()))
                .thenThrow(new LlmRequestException("m1 is down"))
                .thenThrow(new LlmRequestException("m2 timeout"))
                .thenReturn(response("""
                        {"isEvent":true,"startLocal":"2026-11-27T15:00"}
                        """));
        var parser = new LlmEventParser(client, MAPPER, "m1", List.of("m2", "m3"), fixedClock());

        var event = parser.parse("завтра в 15:00", BERLIN);

        assertThat(event.startLocal()).isEqualTo(LocalDateTime.of(2026, 11, 27, 15, 0));

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(client, times(3)).post(captor.capture());
        assertThat(captor.getAllValues().stream().map(ChatRequest::model))
                .containsExactly("m1", "m2", "m3");
    }

    @Test
    void stopsAfterTotalBudgetIsSpent() {
        var clock = new MutableClock(Instant.parse("2026-11-26T10:00:00Z"));
        // Первая попытка "длится" 20 секунд: остатка 5 с не хватает на целую попытку в 12 с.
        when(client.post(any())).thenAnswer(invocation -> {
            clock.advance(Duration.ofSeconds(20));
            throw new LlmRequestException("m1 is slow");
        });
        var parser = new LlmEventParser(client, MAPPER, "m1", List.of("m2"), clock);

        assertThatThrownBy(() -> parser.parse("завтра в 15:00", BERLIN))
                .isInstanceOf(LlmRequestException.class)
                .hasMessageContaining("all models failed");

        verify(client, times(1)).post(any());
    }

    @Test
    void badStartLocalTriesNextModelThenFails() {
        when(client.post(any())).thenReturn(response("""
                {"isEvent":true,"startLocal":"soon"}
                """));

        assertThatThrownBy(() ->
                new LlmEventParser(client, MAPPER, "m1", List.of(), fixedClock()).parse("завтра", BERLIN))
                .isInstanceOf(LlmRequestException.class)
                .hasMessageContaining("all models failed");
    }

    @Test
    void nonObjectContentIsRejected() {
        when(client.post(any())).thenReturn(response("\"just text\""));

        assertThatThrownBy(() ->
                new LlmEventParser(client, MAPPER, "m1", List.of(), fixedClock()).parse("завтра", BERLIN))
                .isInstanceOf(LlmRequestException.class)
                .hasMessageContaining("non-object");
    }

    @Test
    void emptyContentIsRejected() {
        when(client.post(any())).thenReturn(response(""));

        assertThatThrownBy(() ->
                new LlmEventParser(client, MAPPER, "m1", List.of(), fixedClock()).parse("завтра", BERLIN))
                .isInstanceOf(LlmRequestException.class)
                .hasMessageContaining("empty content");
    }

    @Test
    void blankInputIsRejectedWithoutClientCall() {
        var parser = new LlmEventParser(client, MAPPER, "m1", List.of(), fixedClock());

        assertThatThrownBy(() -> parser.parse("   ", BERLIN))
                .isInstanceOf(LlmRequestException.class)
                .hasMessageContaining("empty input");
        verifyNoInteractions(client);
    }

    @Test
    void modelConfigMustNotBeEmpty() {
        assertThatThrownBy(() -> new LlmEventParser(client, MAPPER, " ", List.of(" "), fixedClock()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("openrouter.model");
    }

    @Test
    void mapsUpdateIntentWithMatchFields() {
        when(client.post(any())).thenReturn(response("""
                {"isEvent":true,"intent":"update","title":"Урок гитары",
                 "startLocal":"2026-11-27T18:00","endLocal":null,"durationMinutes":null,
                 "allDay":false,
                 "match":{"date":"2026-11-25","time":"15:00","title":"урок гитары"},
                 "missing":[],"ambiguous":false}
                """));
        var parser = new LlmEventParser(client, MAPPER, "m1", List.of(), fixedClock());

        var event = parser.parse("перенеси урок гитары на завтра 18:00", BERLIN);

        assertThat(event.intent()).isEqualTo(Intent.UPDATE);
        assertThat(event.startLocal()).isEqualTo(LocalDateTime.of(2026, 11, 27, 18, 0));
        assertThat(event.matchDate()).isEqualTo(LocalDate.of(2026, 11, 25));
        assertThat(event.matchTime()).isEqualTo(LocalTime.of(15, 0));
        assertThat(event.matchTitle()).isEqualTo("урок гитары");
    }

    @Test
    void mapsDeleteIntentWithoutNewTime() {
        when(client.post(any())).thenReturn(response("""
                {"isEvent":true,"intent":"delete","title":null,
                 "startLocal":null,"endLocal":null,"durationMinutes":null,
                 "allDay":false,
                 "match":{"date":"2026-11-27","time":null,"title":"репетиция"},
                 "missing":[],"ambiguous":false}
                """));
        var parser = new LlmEventParser(client, MAPPER, "m1", List.of(), fixedClock());

        var event = parser.parse("удали запись про репетицию в четверг", BERLIN);

        assertThat(event.intent()).isEqualTo(Intent.DELETE);
        assertThat(event.startLocal()).isNull();
        assertThat(event.matchDate()).isEqualTo(LocalDate.of(2026, 11, 27));
        assertThat(event.matchTime()).isNull();
        assertThat(event.matchTitle()).isEqualTo("репетиция");
    }

    @Test
    void unknownOrAbsentIntentMeansCreate() {
        when(client.post(any())).thenReturn(response("""
                {"isEvent":true,"intent":"move","startLocal":"2026-11-27T15:00"}
                """));
        var parser = new LlmEventParser(client, MAPPER, "m1", List.of(), fixedClock());

        assertThat(parser.parse("завтра в 15:00", BERLIN).intent()).isEqualTo(Intent.CREATE);
    }

    @Test
    void legacyResponseWithoutNewFieldsDefaultsToCreate() {
        // Ответ по схеме v1: без intent, match и allDay — должен разбираться как раньше.
        when(client.post(any())).thenReturn(response("""
                {"isEvent":true,"title":"Гитара","startLocal":"2026-11-27T15:00",
                 "missing":[],"ambiguous":false}
                """));
        var parser = new LlmEventParser(client, MAPPER, "m1", List.of(), fixedClock());

        var event = parser.parse("завтра в 15:00 урок гитары", BERLIN);

        assertThat(event.intent()).isEqualTo(Intent.CREATE);
        assertThat(event.matchDate()).isNull();
        assertThat(event.matchTime()).isNull();
        assertThat(event.matchTitle()).isNull();
        assertThat(event.startAt(BERLIN)).isEqualTo(Instant.parse("2026-11-27T14:00:00Z"));
    }

    @Test
    void allDayMarksTimeAsMissing() {
        // all-day = дата в startLocal с временем 00:00 + "time" в missing:
        // иначе валидатору неоткуда взять дату для проверки прошлого и напоминания.
        when(client.post(any())).thenReturn(response("""
                {"isEvent":true,"intent":"create","title":"Репетиция",
                 "startLocal":"2026-11-27T00:00","allDay":true,
                 "missing":[],"ambiguous":false}
                """));
        var parser = new LlmEventParser(client, MAPPER, "m1", List.of(), fixedClock());

        var event = parser.parse("завтра репетиция, весь день", BERLIN);

        assertThat(event.startLocal()).isEqualTo(LocalDateTime.of(2026, 11, 27, 0, 0));
        assertThat(event.ambiguous()).isFalse();
        assertThat(event.missing()).containsExactly("time");
    }

    @Test
    void badMatchValuesAreIgnoredInsteadOfFailing() {
        // match — опорные точки поиска: мусор в них не должен валить весь разбор.
        when(client.post(any())).thenReturn(response("""
                {"isEvent":true,"intent":"update","startLocal":"2026-11-27T18:00",
                 "match":{"date":"soon","time":"25:99","title":" "},
                 "missing":[],"ambiguous":false}
                """));
        var parser = new LlmEventParser(client, MAPPER, "m1", List.of(), fixedClock());

        var event = parser.parse("перенеси", BERLIN);

        assertThat(event.intent()).isEqualTo(Intent.UPDATE);
        assertThat(event.matchDate()).isNull();
        assertThat(event.matchTime()).isNull();
        assertThat(event.matchTitle()).isNull();
        assertThat(event.startLocal()).isEqualTo(LocalDateTime.of(2026, 11, 27, 18, 0));
    }

    @Test
    void schemaAndPromptDeclareIntentMatchAndAllDay() {
        assertThat(EventPrompts.SCHEMA_JSON)
                .contains("\"intent\"")
                .contains("\"match\"")
                .contains("\"allDay\"");

        when(client.post(any())).thenReturn(response("""
                {"isEvent":true,"startLocal":"2026-11-27T15:00"}
                """));
        var parser = new LlmEventParser(client, MAPPER, "m1", List.of(), fixedClock());
        parser.parse("завтра в 15:00", BERLIN);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(client).post(captor.capture());
        var system = captor.getValue().messages().get(0).content();
        assertThat(system).contains("intent").contains("allDay").contains("перенеси урок гитары");
        assertThat(captor.getValue().responseFormat().jsonSchema().strict()).isTrue();
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-11-26T10:00:00Z"), ZoneOffset.UTC);
    }

    private static ChatRequest.Response response(String content) {
        return new ChatRequest.Response(
                List.of(new ChatRequest.Choice(new ChatRequest.Message("assistant", content), "stop")),
                null);
    }

    /** Управляемые часы: бюджет парсера можно двигать без ожиданий в тестах. */
    static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant start) {
            this.instant = start;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
