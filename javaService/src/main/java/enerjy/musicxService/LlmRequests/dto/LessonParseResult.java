package enerjy.musicxService.LlmRequests.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record LessonParseResult(
        LocalDate date,
        LocalTime time,
        String title,
        String note
) {
}