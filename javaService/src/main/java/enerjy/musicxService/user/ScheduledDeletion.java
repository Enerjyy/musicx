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
 * Задача отложенного удаления подтверждения создания записи.
 *
 * Хранится в ядре (единственный владелец состояния): рестарт транспортного
 * сервиса или ядра не оставляет «висящее» сообщение — просроченные задачи
 * выполняются на первом же тике планировщика.
 *
 * Уникальность (channel, conversation_id, external_message_id) защищает от
 * дублей при повторной доставке DeliveryResult.
 */
@Entity
@Table(
        name = "scheduled_deletions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_scheduled_deletions_message",
                columnNames = {"channel", "conversation_id", "external_message_id"}
        )
)
@Data
public class ScheduledDeletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String channel;

    @Column(name = "conversation_id", nullable = false, length = 255)
    private String conversationId;

    @Column(name = "external_message_id", nullable = false, length = 64)
    private String externalMessageId;

    /** Когда удалять: момент получения подтверждения + задержка. */
    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "delete_at", nullable = false)
    private Instant deleteAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeletionStatus status;

    @Column(nullable = false)
    private int attempts;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    private Instant claimedAt;

    /** commandId последней команды удаления — по нему DeliveryResult находит задачу. */
    @Column(name = "command_id", length = 64)
    private String commandId;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
