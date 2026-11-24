package enerjy.musicxService.Logic.schedule;

import enerjy.musicxService.LlmRequests.LessonParser;
import enerjy.musicxService.LlmRequests.dto.LessonParseResult;
import enerjy.musicxService.user.userSchedules;
import enerjy.musicxService.user.userSchedulesRepo;
import org.springframework.stereotype.Service;

@Service
public class handleSchedule {

    private LessonParser lessonParser;
    private userSchedulesRepo userSchedulesRepo;

    public handleSchedule(LessonParser lessonParser, userSchedulesRepo userSchedulesRepo) {
        this.lessonParser=lessonParser;
        this.userSchedulesRepo = userSchedulesRepo;
    }

    public void handleNewSchedule(String text, Long userId) {
        LessonParseResult result = lessonParser.parse(text);

        if (result.date() == null) {
            throw new ScheduleException("date is required");
        }
        if (result.time() == null) {
            throw new ScheduleException("time is required");
        }

        userSchedules userSchedules = new userSchedules();
        userSchedules.setUserId(userId);
        userSchedules.setDate(result.date());
        userSchedules.setTime(result.time());
        userSchedules.setNote(result.note());
        userSchedules.setTitle(result.title());

        userSchedulesRepo.save(userSchedules);
    }

}
