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
public interface ScheduledDeletionRepo extends JpaRepository<ScheduledDeletion, Long> {

    boolean existsByChannelAndConversationIdAndExternalMessageId(
            String channel,
            String conversationId,
            String externalMessageId
    );

    /**
     * Просроченные задачи с блокировкой строк: параллельные тики (или второй
     * инстанс) не схватят одни и те же задачи. Подсказка
     * {@code jakarta.persistence.lock.timeout = -2} включает SKIP LOCKED
     * там, где диалект её поддерживает (Postgres; H2 игнорирует безвредно).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<ScheduledDeletion> findByStatusAndDeleteAtLessThanEqual(
            DeletionStatus status,
            Instant deleteAt,
            Pageable pageable
    );

    /** Зависшие в SENDING (рестарт/сбой между захватом и результатом) — обратно в очередь. */
    @Modifying(clearAutomatically = true)
    @Query("update ScheduledDeletion d set d.status = :to, d.claimedAt = null "
            + "where d.status = :from and d.claimedAt < :deadline")
    int returnStuckToPending(
            @Param("to") DeletionStatus to,
            @Param("from") DeletionStatus from,
            @Param("deadline") Instant deadline
    );
}
