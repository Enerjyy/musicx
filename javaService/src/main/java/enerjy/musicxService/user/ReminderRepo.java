package enerjy.musicxService.user;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ReminderRepo extends JpaRepository<Reminder, Long> {

    List<Reminder> findByScheduleId(Long scheduleId);

    /**
     * Напоминания, пора отправлять: remind_at наступил, событие ещё не началось.
     * Строковые блокировки (PESSIMISTIC_WRITE) + подсказка SKIP LOCKED не дают
     * параллельным тикам/инстансам схватить одни и те же строки.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select r from Reminder r, userSchedules s
            where r.scheduleId = s.sceduleId
              and r.status = :status
              and r.remindAt <= :now
              and s.startAt > :now
            """)
    List<Reminder> lockDue(
            @Param("status") ReminderStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );

    /** Зависшие в SENDING (рестарт/сбой между захватом и результатом) — обратно в очередь. */
    @Modifying(clearAutomatically = true)
    @Query("update Reminder r set r.status = :to, r.claimedAt = null "
            + "where r.status = :from and r.claimedAt < :deadline")
    int returnStuckToPending(
            @Param("to") ReminderStatus to,
            @Param("from") ReminderStatus from,
            @Param("deadline") Instant deadline
    );

    /** Событие уже началось — напоминание отправлять поздно. */
    @Modifying(clearAutomatically = true)
    @Query("update Reminder r set r.status = :to "
            + "where r.status = :from "
            + "and exists (select 1 from userSchedules s "
            + "            where s.sceduleId = r.scheduleId and s.startAt < :now)")
    int expirePassed(
            @Param("to") ReminderStatus to,
            @Param("from") ReminderStatus from,
            @Param("now") Instant now
    );
}
