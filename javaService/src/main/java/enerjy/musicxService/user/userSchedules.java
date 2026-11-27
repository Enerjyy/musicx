package enerjy.musicxService.user;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Запись расписания.
 *
 * Время хранится двумя способами:
 * <ul>
 *   <li>{@code date}/{@code time} — локальные значения в зоне записи; их читает
 *       {@code /calendar}, поэтому старый фронт работает без изменений;</li>
 *   <li>{@code startAt}/{@code endAt} — абсолютные моменты времени (timestamptz);
 *       по ним считаются напоминания и проверки «прошлое/будущее».</li>
 * </ul>
 * {@code timezone} — снимок зоны пользователя на момент создания: смена зоны
 * позже не сдвигает уже созданные записи.
 *
 * Новые колонки nullable, чтобы записи, созданные до появления валидатора,
 * сохранялись как раньше; ограничение NOT NULL будет добавлено, когда путь
 * через валидатор станет единственным. {@code status} NOT NULL с дефолтом:
 * сущность инициализирует его значением {@link ScheduleStatus#ACTIVE}.
 */
@Entity
@Data
public class userSchedules {

    /** Не переименовывать: историческая опечатка, на неё завязаны БД и код. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sceduleId;

    @Column(nullable = false)
    private Long userId;

    /** Локальная дата в зоне записи; остаётся источником для /calendar. */
    @Column(nullable = false)
    private LocalDate date;

    /** Локальное время в зоне записи; null для записей «весь день» (V11). */
    @Column
    private LocalTime time;

    @Column(length = 200)
    private String title;

    @Column(length = 1000)
    private String note;

    /** Начало как абсолютный момент времени. */
    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    private Instant startAt;

    /** Конец как абсолютный момент времени; null для записей без рассчитанного конца. */
    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    private Instant endAt;

    /** Снимок IANA-зоны пользователя на момент создания, например Europe/Berlin. */
    @Column(length = 64)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ScheduleStatus status = ScheduleStatus.ACTIVE;

    /** Канал, из которого создана запись (контракт messaging), может быть null. */
    @Column(length = 32)
    private String sourceChannel;

    /** Идентификатор диалога в канале, может быть null. */
    @Column(length = 255)
    private String sourceConversationId;

    /** Идентификатор сообщения в канале; тройка с channel+conversation даёт дедупликацию. */
    @Column(length = 64)
    private String sourceMessageId;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    private Instant createdAt;
}
