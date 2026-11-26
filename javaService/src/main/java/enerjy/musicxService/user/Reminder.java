package enerjy.musicxService.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Напоминание по записи.
 *
 * Уникальность (schedule_id, kind) — защита от дублей: сколько бы раз ни
 * обрабатывалось событие создания, напоминание одного типа ровно одно.
 */
@Entity
@Table(
        name = "reminders",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reminders_schedule_kind",
                columnNames = {"schedule_id", "kind"}
        )
)
@Data
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReminderKind kind;

    /** Когда отправлять: start_at минус смещение. */
    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "remind_at", nullable = false)
    private Instant remindAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReminderStatus status;

    @Column(nullable = false)
    private int attempts;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    private Instant claimedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    private Instant sentAt;
}
