package enerjy.musicxService.messaging;

import enerjy.musicxService.user.userSchedules;

import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Тексты для пользователя. Канал-агностичны: форматирует ядро, транспорт не меняет. */
public final class MessageTemplates {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /** Этапы статуса создания записи (черновик в канале). */
    public static final String PARSING = "Разбираю запрос…";
    public static final String VALIDATING = "Проверяю дату и время…";
    public static final String SAVING = "Сохраняю…";

    /** Сбой разбора: повторная попытка безопасна, автоудаление не нужно. */
    public static final String LLM_FAILED =
            "Не смог разобрать сообщение. Попробуйте ещё раз чуть позже.";

    private MessageTemplates() {
    }

    /** Подтверждение создания: название, дата и время в зоне записи, длительность, описание. */
    public static String confirmation(userSchedules schedule) {
        var zone = ZoneId.of(schedule.getTimezone());
        var text = new StringBuilder("Запись создана");
        if (schedule.getTitle() != null) {
            text.append(": ").append(schedule.getTitle());
        }
        text.append('\n')
                .append("Когда: ")
                .append(schedule.getStartAt().atZone(zone).format(DATE_TIME));
        if (schedule.getEndAt() != null) {
            long minutes = Duration.between(schedule.getStartAt(), schedule.getEndAt()).toMinutes();
            text.append('–')
                    .append(schedule.getEndAt().atZone(zone).format(TIME))
                    .append(" (длительность ").append(duration(minutes)).append(')');
        }
        if (schedule.getNote() != null) {
            text.append("\nОписание: ").append(schedule.getNote());
        }
        return text.toString();
    }

    /** Напоминание: за сколько до начала, название и локальное время события. */
    public static String reminder(userSchedules schedule, Duration offset) {
        var zone = schedule.getTimezone() != null
                ? ZoneId.of(schedule.getTimezone())
                : java.time.ZoneOffset.UTC;
        var text = new StringBuilder("Напоминание: ");
        long minutes = offset.toMinutes();
        text.append(minutes > 0 ? "через " + duration(minutes) + " — " : "уже скоро — ");
        if (schedule.getTitle() != null) {
            text.append('«').append(schedule.getTitle()).append("» ");
        }
        text.append(schedule.getStartAt().atZone(zone).format(DATE_TIME));
        if (schedule.getNote() != null) {
            text.append("\nОписание: ").append(schedule.getNote());
        }
        return text.toString();
    }

    static String duration(long minutes) {
        long hours = minutes / 60;
        long rest = minutes % 60;
        if (hours == 0) {
            return rest + " мин";
        }
        if (rest == 0) {
            return hours + " ч";
        }
        return hours + " ч " + rest + " мин";
    }
}
