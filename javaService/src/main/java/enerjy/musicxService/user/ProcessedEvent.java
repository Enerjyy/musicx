package enerjy.musicxService.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Отметка «событие уже обработано этим консьюмером» — идемпотентность при
 * at-least-once доставке Kafka: повторное событие не создаёт дубликатов.
 */
@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEventId.class)
@Data
public class ProcessedEvent {

    public ProcessedEvent() {
    }

    @Id
    @Column(name = "event_id", length = 64)
    private String eventId;

    @Id
    @Column(length = 64)
    private String consumer;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
