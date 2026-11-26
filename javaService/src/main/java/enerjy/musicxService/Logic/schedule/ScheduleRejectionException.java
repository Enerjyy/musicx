package enerjy.musicxService.Logic.schedule;

/** Отказ валидатора с понятным пользователю сообщением (подсказкой). */
public class ScheduleRejectionException extends ScheduleException {

    public ScheduleRejectionException(String userHint) {
        super(userHint);
    }
}
