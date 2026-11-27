package enerjy.musicxService.api.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarEntryDtoTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesIdAndEndTime() throws Exception {
        var dto = new CalendarEntryDto(5L, "2026-11-27", "15:00", "16:00", "Урок", "заметка", null);

        var json = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(json.get("id").asLong()).isEqualTo(5L);
        assertThat(json.get("date").asText()).isEqualTo("2026-11-27");
        assertThat(json.get("time").asText()).isEqualTo("15:00");
        assertThat(json.get("endTime").asText()).isEqualTo("16:00");
        assertThat(json.get("title").asText()).isEqualTo("Урок");
        assertThat(json.get("note").asText()).isEqualTo("заметка");
    }

    /** Старый фронт читает date/time/title/note и игнорирует неизвестные поля. */
    @Test
    void oldClientStillSeesAllLegacyFields() throws Exception {
        var dto = new CalendarEntryDto(5L, "2026-11-27", "15:00", null, null, null, null);

        var json = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(json.get("date").asText()).isEqualTo("2026-11-27");
        assertThat(json.get("time").asText()).isEqualTo("15:00");
        // id и endTime есть в ответе, старый клиент их просто не читает.
        assertThat(json.get("id").asLong()).isEqualTo(5L);
        // Пустые поля не сериализуются.
        assertThat(json.has("endTime")).isFalse();
        assertThat(json.has("title")).isFalse();
        assertThat(json.has("note")).isFalse();
        assertThat(json.has("allDay")).isFalse();
    }

    /** Безвременная запись: time отсутствует, allDay = true. */
    @Test
    void allDayEntrySerializesWithoutTimeAndWithAllDayTrue() throws Exception {
        var dto = new CalendarEntryDto(7L, "2026-11-28", null, null, "Репетиция", null, Boolean.TRUE);

        var json = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(json.get("allDay").asBoolean()).isTrue();
        assertThat(json.has("time")).isFalse();
        assertThat(json.has("endTime")).isFalse();
        assertThat(json.get("title").asText()).isEqualTo("Репетиция");
    }

    @Test
    void deserializesJsonWithUnknownFields() throws Exception {
        var json = """
                {"id":1,"date":"2026-11-27","time":"15:00","endTime":"16:00",
                 "title":"Урок","note":"","addedLater":"x"}
                """;

        var dto = mapper.readValue(json, CalendarEntryDto.class);

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.endTime()).isEqualTo("16:00");
    }
}
