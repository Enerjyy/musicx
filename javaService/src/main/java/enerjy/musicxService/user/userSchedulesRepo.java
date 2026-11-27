package enerjy.musicxService.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface userSchedulesRepo extends JpaRepository<userSchedules, Long> {
    List<userSchedules> findByUserIdAndDateBetweenOrderByTimeAsc(
            Long userId,
            LocalDate from,
            LocalDate to
    );

    /**
     * Записи месяца только в живом статусе: отменённые (CANCELED после удаления
     * из чата) не должны попадать в календарь.
     */
    List<userSchedules> findByUserIdAndStatusAndDateBetweenOrderByTimeAsc(
            Long userId,
            ScheduleStatus status,
            LocalDate from,
            LocalDate to
    );

    /**
     * Кандидаты для матчинга при правке/удалении из чата: записи пользователя
     * за конкретный день. Отменённые записи не участвуют — их нельзя ни
     * перенести, ни «удалить повторно».
     */
    List<userSchedules> findByUserIdAndStatusAndDate(
            Long userId,
            ScheduleStatus status,
            LocalDate date
    );

    /** Поиск по тройке источника — идемпотентность повторной доставки сообщений. */
    Optional<userSchedules> findBySourceChannelAndSourceConversationIdAndSourceMessageId(
            String sourceChannel,
            String sourceConversationId,
            String sourceMessageId
    );

    long countByUserId(Long userId);
}
