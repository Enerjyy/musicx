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

    /** Поиск по тройке источника — идемпотентность повторной доставки сообщений. */
    Optional<userSchedules> findBySourceChannelAndSourceConversationIdAndSourceMessageId(
            String sourceChannel,
            String sourceConversationId,
            String sourceMessageId
    );

    long countByUserId(Long userId);
}
