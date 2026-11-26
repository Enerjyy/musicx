package enerjy.musicxService.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Журнал команд ядра транспорту (таблица outbound_commands).
 *
 * Создаётся в той же транзакции, что и породившее состояние (запись/напоминание),
 * поэтому повторная обработка события видит «команду без результата» и может
 * повторить отправку. DeliveryResult из канала обновляет статус и запоминает
 * external_message_id (он нужен последующим удалениям).
 */
@Entity
@Table(name = "outbound_commands")
@Data
public class CommandJournalEntry {

    @Id
    @Column(name = "command_id", length = 64)
    private String commandId;

    /** Тип команды из контракта (SendMessage.TYPE и т.п.). */
    @Column(nullable = false, length = 32)
    private String type;

    @Column(nullable = false, length = 32)
    private String channel;

    @Column(name = "conversation_id", nullable = false, length = 255)
    private String conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CommandPurpose purpose;

    /** С чем связана команда: scheduleId / reminderId. */
    @Column(name = "related_id")
    private Long relatedId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CommandDeliveryStatus status;

    @Column(name = "external_message_id", length = 64)
    private String externalMessageId;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
