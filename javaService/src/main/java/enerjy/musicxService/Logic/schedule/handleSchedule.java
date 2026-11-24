package enerjy.musicxService.Logic.schedule;

import enerjy.musicxService.LlmRequests.LessonParser;
import enerjy.musicxService.LlmRequests.dto.LessonParseResult;
import enerjy.musicxService.user.userEntity;
import enerjy.musicxService.user.userSchedules;
import enerjy.musicxService.user.userSchedulesRepo;
import enerjy.musicxService.user.userService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class handleSchedule {

    private final LessonParser lessonParser;
    private final userSchedulesRepo userSchedulesRepo;
    private final userService userService;

    public handleSchedule(
            LessonParser lessonParser,
            userSchedulesRepo userSchedulesRepo,
            userService userService
    ) {
        this.lessonParser = lessonParser;
        this.userSchedulesRepo = userSchedulesRepo;
        this.userService = userService;
    }

    @Transactional
    public void handleNewSchedule(String text, Long telegramId) {
        LessonParseResult result = lessonParser.parse(text);

        if (result.date() == null || result.time() == null) {
            throw new ScheduleException("date and time are required");
        }

        userEntity user = userService.upsertByTelegramId(telegramId);

        userSchedules entry = new userSchedules();
        entry.setUserId(user.getUserId());
        entry.setDate(result.date());
        entry.setTime(result.time());
        entry.setTitle(blankToNull(result.title()));
        entry.setNote(blankToNull(result.note()));

        userSchedulesRepo.save(entry);
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}