package enerjy.musicxService.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReminderRepo extends JpaRepository<Reminder, Long> {

    List<Reminder> findByScheduleId(Long scheduleId);
}
