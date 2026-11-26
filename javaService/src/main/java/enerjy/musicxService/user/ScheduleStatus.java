package enerjy.musicxService.user;

/**
 * Жизненный цикл записи расписания.
 *
 * Строка пишется в БД как строка (`@Enumerated(EnumType.STRING)`), поэтому
 * новое значение не требует миграции.
 */
public enum ScheduleStatus {

    /** Активная запись. */
    ACTIVE,

    /** Отменена пользователем; напоминания по ней не отправляются. */
    CANCELLED
}
