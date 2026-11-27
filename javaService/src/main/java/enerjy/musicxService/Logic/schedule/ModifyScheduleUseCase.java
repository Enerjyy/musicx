package enerjy.musicxService.Logic.schedule;

import enerjy.musicxService.Logic.schedule.parse.Intent;
import enerjy.musicxService.Logic.schedule.parse.ParsedEvent;
import enerjy.musicxService.messaging.MessageTemplates;
import enerjy.musicxService.messaging.contract.Channels;
import enerjy.musicxService.messaging.contract.IncomingMessage;
import enerjy.musicxService.messaging.contract.SendMessage;
import enerjy.musicxService.messaging.contract.ShowProgress;
import enerjy.musicxService.messaging.channel.ChannelRegistry;
import enerjy.musicxService.messaging.channel.OutboundChannel;
import enerjy.musicxService.security.AuthProvider;
import enerjy.musicxService.user.CommandDeliveryStatus;
import enerjy.musicxService.user.CommandJournalEntry;
import enerjy.musicxService.user.CommandJournalRepo;
import enerjy.musicxService.user.CommandPurpose;
import enerjy.musicxService.user.ProcessedEvent;
import enerjy.musicxService.user.ProcessedEventId;
import enerjy.musicxService.user.ProcessedEventRepo;
import enerjy.musicxService.user.Reminder;
import enerjy.musicxService.user.ReminderKind;
import enerjy.musicxService.user.ReminderRepo;
import enerjy.musicxService.user.ReminderStatus;
import enerjy.musicxService.user.ScheduleStatus;
import enerjy.musicxService.user.userEntity;
import enerjy.musicxService.user.userSchedules;
import enerjy.musicxService.user.userSchedulesRepo;
import enerjy.musicxService.user.userService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Сценарий «перенести или удалить существующую запись из чата».
 *
 * Вызывается из {@link CreateScheduleUseCase} после парсинга, если модель
 * распознала интент UPDATE/DELETE. Дедупликация общая с созданием: тот же
 * {@code consumer} в processed_events, поэтому повторная доставка сообщения
 * не выполняет матчинг второй раз.
 *
 * <p>Матчинг кандидатов (решение по записи):
 * <ol>
 *   <li>запрос в репозиторий: записи пользователя за день из опорной точки,
 *       статус ACTIVE — отменённые ни перенести, ни удалить нельзя;</li>
 *   <li>нечёткое сужение в коде: время в окне ±30 минут (если указано) и
 *       название подстрокой в любую сторону без учёта регистра;</li>
 *   <li>0 или &gt;1 кандидатов — уточняющий вопрос в чат, запись не трогаем;</li>
 *   <li>ровно 1 — в одной транзакции правка (или CANCELED) + пересоздание
 *       напоминания + журнал, подтверждение строго после коммита.</li>
 * </ol>
 */
@Service
public class ModifyScheduleUseCase {

    /** Окно матчинга по времени: «перенеси урок в 15:00» должно находить запись в 15:10. */
    static final Duration MATCH_TIME_TOLERANCE = Duration.ofMinutes(30);

    private static final Logger log = LoggerFactory.getLogger(ModifyScheduleUseCase.class);

    private final ScheduleValidator validator;
    private final userSchedulesRepo schedulesRepo;
    private final userService userService;
    private final ProcessedEventRepo processedEvents;
    private final ReminderRepo reminders;
    private final CommandJournalRepo commandJournal;
    private final ChannelRegistry channels;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final ZoneId defaultZone;
    private final Duration reminderOffset;

    @Autowired
    public ModifyScheduleUseCase(
            ScheduleValidator validator,
            userSchedulesRepo schedulesRepo,
            userService userService,
            ProcessedEventRepo processedEvents,
            ReminderRepo reminders,
            CommandJournalRepo commandJournal,
            ChannelRegistry channels,
            TransactionTemplate transactions,
            @Value("${app.time-zone:Europe/Moscow}") String timeZone,
            @Value("${app.reminders.offset:PT3H}") Duration reminderOffset
    ) {
        this(validator, schedulesRepo, userService, processedEvents, reminders,
                commandJournal, channels, transactions,
                Clock.systemUTC(), ZoneId.of(timeZone), reminderOffset);
    }

    ModifyScheduleUseCase(
            ScheduleValidator validator,
            userSchedulesRepo schedulesRepo,
            userService userService,
            ProcessedEventRepo processedEvents,
            ReminderRepo reminders,
            CommandJournalRepo commandJournal,
            ChannelRegistry channels,
            TransactionTemplate transactions,
            Clock clock,
            ZoneId defaultZone,
            Duration reminderOffset
    ) {
        if (reminderOffset.isNegative() || reminderOffset.isZero()) {
            throw new IllegalArgumentException("reminder offset must be positive");
        }
        this.validator = validator;
        this.schedulesRepo = schedulesRepo;
        this.userService = userService;
        this.processedEvents = processedEvents;
        this.reminders = reminders;
        this.commandJournal = commandJournal;
        this.channels = channels;
        this.transactions = transactions;
        this.clock = clock;
        this.defaultZone = defaultZone;
        this.reminderOffset = reminderOffset;
    }

    /**
     * Обработка уже распарсенного сообщения с интентом UPDATE/DELETE.
     * Прогресс «Проверяю/Сохраняю» публикует сам: «Разбираю…» уже отправил
     * вызывающий сценарий до парсинга.
     */
    public void handle(IncomingMessage message, ParsedEvent parsed) {
        var channel = channels.by(message.channel());
        var conversationId = message.conversationId();

        // Повторная доставка: событие уже обработано (возможно, запись уже
        // перенесена или отменена), матчить и отвечать второй раз не нужно.
        if (processedEvents.existsById(
                new ProcessedEventId(message.eventId(), CreateScheduleUseCase.CONSUMER_ID))) {
            log.info("duplicate delivery of modify intent, eventId={}", message.eventId());
            return;
        }

        channel.publish(ShowProgress.create(
                message.channel(), conversationId, message.progressRef(), MessageTemplates.VALIDATING));

        // Для переноса новое время проходит ту же приёмку, что и при создании;
        // для удаления валидировать нечего — нового времени нет.
        ValidatedSchedule validated = null;
        if (parsed.intent() == Intent.UPDATE) {
            try {
                validated = validator.validate(parsed, defaultZone);
            } catch (ScheduleRejectionException e) {
                markProcessed(message);
                channel.publish(SendMessage.create(
                        message.channel(), conversationId, newCommandId(), e.getMessage()));
                return;
            }
        }

        channel.publish(ShowProgress.create(
                message.channel(), conversationId, message.progressRef(), MessageTemplates.SAVING));

        var user = userService.upsertByIdentity(
                providerOf(message.channel()), message.userRef(), null, null);

        LocalDate matchDate = resolveMatchDate(parsed);
        if (matchDate == null) {
            // Уточнение фиксируем в processed_events: повторная доставка не должна
            // снова тратить LLM на то же сообщение, чтобы получить тот же вопрос.
            markProcessed(message);
            clarify(message, channel, MessageTemplates.CLARIFY_WHICH, null);
            return;
        }
        List<userSchedules> candidates = matchCandidates(user.getUserId(), matchDate, parsed);

        if (candidates.size() != 1) {
            // 0 — не нашли; больше 1 — рискуем тронуть не ту запись: уточняем.
            String clarifyText = candidates.isEmpty()
                    ? MessageTemplates.CLARIFY_NOT_FOUND
                    : MessageTemplates.CLARIFY_SEVERAL;
            log.info("modify intent matched {} candidates, userId={}, date={}",
                    candidates.size(), user.getUserId(), matchDate);
            markProcessed(message);
            clarify(message, channel, clarifyText, null);
            return;
        }

        sendAfterPersist(message, channel, parsed.intent(), validated, candidates.get(0));
    }

    /** Опорная дата для поиска: из match-поля, иначе из новой даты события. */
    private static LocalDate resolveMatchDate(ParsedEvent parsed) {
        if (parsed.matchDate() != null) {
            return parsed.matchDate();
        }
        return parsed.startLocal() == null ? null : parsed.startLocal().toLocalDate();
    }

    /** Кандидаты за день, суженные по времени (±окно) и названию (нечётко). */
    private List<userSchedules> matchCandidates(Long userId, LocalDate date, ParsedEvent parsed) {
        List<userSchedules> byDate = schedulesRepo.findByUserIdAndStatusAndDate(
                userId, ScheduleStatus.ACTIVE, date);
        return byDate.stream()
                .filter(schedule -> timeMatches(schedule, parsed.matchTime()))
                .filter(schedule -> titleMatches(schedule.getTitle(), parsed.matchTitle()))
                .toList();
    }

    /**
     * Время: без опорного времени проходят все записи дня; с опорным — записи,
     * чьё локальное время отличается не больше чем на {@link #MATCH_TIME_TOLERANCE}.
     * Безвременные записи под «перенеси в 15:00» не подпадают: времени у них нет.
     */
    private static boolean timeMatches(userSchedules schedule, LocalTime matchTime) {
        if (matchTime == null) {
            return true;
        }
        if (schedule.getTime() == null) {
            return false;
        }
        long diffMinutes = Duration.between(schedule.getTime(), matchTime).abs().toMinutes();
        return diffMinutes <= MATCH_TIME_TOLERANCE.toMinutes();
    }

    /**
     * Название: подстрока в любую сторону без учёта регистра («урок» найдёт
     * «Урок гитары»). Пустая опора — не фильтруем, иначе совпадение времени
     * или дня уже достаточно.
     */
    private static boolean titleMatches(String scheduleTitle, String matchTitle) {
        if (matchTitle == null || matchTitle.isBlank()) {
            return true;
        }
        if (scheduleTitle == null) {
            return false;
        }
        var haystack = scheduleTitle.toLowerCase(Locale.ROOT);
        var needle = matchTitle.toLowerCase(Locale.ROOT).trim();
        return haystack.contains(needle) || needle.contains(haystack);
    }

    private void sendAfterPersist(
            IncomingMessage message,
            OutboundChannel channel,
            Intent intent,
            ValidatedSchedule validated,
            userSchedules schedule
    ) {
        var conversationId = message.conversationId();
        var commandId = newCommandId();
        boolean update = intent == Intent.UPDATE;
        try {
            transactions.executeWithoutResult(status -> {
                if (processedEvents.existsById(
                        new ProcessedEventId(message.eventId(), CreateScheduleUseCase.CONSUMER_ID))) {
                    throw new AlreadyProcessedException();
                }
                processedEvents.save(processed(message));

                if (update) {
                    applyUpdate(schedule, validated);
                    // Напоминания старого времени больше не актуальны: удаляем
                    // и создаём заново под новое время (решение 5).
                    reminders.deleteByScheduleId(schedule.getSceduleId());
                    createReminder(schedule, validated);
                } else {
                    // Удаление мягкое: история сохраняется, /calendar отфильтрует.
                    schedule.setStatus(ScheduleStatus.CANCELLED);
                    // PENDING-напоминания отменённой записи отправлять нельзя.
                    reminders.deleteByScheduleId(schedule.getSceduleId());
                }
                schedulesRepo.save(schedule);

                var purpose = update ? CommandPurpose.CONFIRM_UPDATE : CommandPurpose.CONFIRM_DELETE;
                commandJournal.save(modifyCommand(commandId, schedule, message, purpose));

                var text = update
                        ? MessageTemplates.confirmUpdate(schedule)
                        : MessageTemplates.confirmDelete(schedule);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        channel.publish(SendMessage.create(
                                message.channel(), conversationId, commandId, text));
                    }
                });
            });
        } catch (AlreadyProcessedException | DataIntegrityViolationException e) {
            // Гонка повторной доставки: событие обработал параллельный поток.
            log.info("modify intent already processed, eventId={}", message.eventId());
        }
    }

    /** Перенос: обновляются локальные поля, абсолютные моменты и зона-снимок. */
    private static void applyUpdate(userSchedules schedule, ValidatedSchedule validated) {
        var zone = validated.zone();
        schedule.setTitle(validated.title());
        schedule.setNote(validated.note());
        schedule.setTimezone(zone.getId());
        if (validated.allDay()) {
            schedule.setDate(validated.remindAt().atZone(zone).toLocalDate());
            schedule.setTime(null);
            schedule.setStartAt(null);
            schedule.setEndAt(null);
        } else {
            schedule.setDate(validated.startAt().atZone(zone).toLocalDate());
            schedule.setTime(validated.startAt().atZone(zone).toLocalTime());
            schedule.setStartAt(validated.startAt());
            schedule.setEndAt(validated.endAt());
        }
    }

    /** Новое напоминание после переноса: T_MINUS_3H или полуночное для «весь день». */
    private void createReminder(userSchedules schedule, ValidatedSchedule validated) {
        var reminder = new Reminder();
        reminder.setScheduleId(schedule.getSceduleId());
        if (validated.allDay()) {
            reminder.setKind(ReminderKind.MIDNIGHT);
            reminder.setRemindAt(validated.remindAt());
        } else {
            reminder.setKind(ReminderKind.T_MINUS_3H);
            reminder.setRemindAt(validated.startAt().minus(reminderOffset));
        }
        reminder.setStatus(reminder.getRemindAt().isAfter(Instant.now(clock))
                ? ReminderStatus.PENDING
                : ReminderStatus.SKIPPED);
        reminders.save(reminder);
    }

    /**
     * Уточняющий вопрос: журнал с purpose CLARIFY_SCHEDULE нужен, чтобы
     * DeliveryResult от канала не упал «неизвестная команда», а подтверждение
     * ушло после коммита, как у остальных команд.
     */
    private void clarify(IncomingMessage message, OutboundChannel channel, String text, Long relatedId) {
        var commandId = newCommandId();
        transactions.executeWithoutResult(status -> {
            var entry = new CommandJournalEntry();
            entry.setCommandId(commandId);
            entry.setType(SendMessage.TYPE);
            entry.setChannel(message.channel());
            entry.setConversationId(message.conversationId());
            entry.setPurpose(CommandPurpose.CLARIFY_SCHEDULE);
            entry.setRelatedId(relatedId);
            entry.setStatus(CommandDeliveryStatus.PENDING);
            entry.setCreatedAt(Instant.now(clock));
            commandJournal.save(entry);

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    channel.publish(SendMessage.create(
                            message.channel(), message.conversationId(), commandId, text));
                }
            });
        });
    }

    private CommandJournalEntry modifyCommand(
            String commandId, userSchedules schedule, IncomingMessage message, CommandPurpose purpose
    ) {
        var entry = new CommandJournalEntry();
        entry.setCommandId(commandId);
        entry.setType(SendMessage.TYPE);
        entry.setChannel(message.channel());
        entry.setConversationId(message.conversationId());
        entry.setPurpose(purpose);
        entry.setRelatedId(schedule.getSceduleId());
        entry.setStatus(CommandDeliveryStatus.PENDING);
        entry.setCreatedAt(Instant.now(clock));
        return entry;
    }

    private void markProcessed(IncomingMessage message) {
        if (processedEvents.existsById(
                new ProcessedEventId(message.eventId(), CreateScheduleUseCase.CONSUMER_ID))) {
            return;
        }
        try {
            processedEvents.save(processed(message));
        } catch (DataIntegrityViolationException e) {
            // Параллельная обработка того же события успела раньше — не страшно.
        }
    }

    private ProcessedEvent processed(IncomingMessage message) {
        var processed = new ProcessedEvent();
        processed.setEventId(message.eventId());
        processed.setConsumer(CreateScheduleUseCase.CONSUMER_ID);
        processed.setProcessedAt(Instant.now(clock));
        return processed;
    }

    private static AuthProvider providerOf(String channel) {
        if (Channels.TELEGRAM.equals(channel)) {
            return AuthProvider.TELEGRAM;
        }
        throw new IllegalArgumentException("no identity provider for channel: " + channel);
    }

    private static String newCommandId() {
        return UUID.randomUUID().toString();
    }

    /** Сигнал «событие уже обработано другим потоком» внутри транзакции. */
    private static final class AlreadyProcessedException extends RuntimeException {
    }
}
