package enerjy.musicxService.Logic.schedule;

import enerjy.musicxService.LlmRequests.LlmRequestException;
import enerjy.musicxService.Logic.schedule.parse.EventParser;
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
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/**
 * Сценарий «пользователь прислал текст — создана запись».
 *
 * Порядок работы с сообщением:
 * <ol>
 *   <li>быстрый поиск записи по тройке (channel, conversation, messageId):
 *       повторная доставка без LLM получает повтор подтверждения, если
 *       DeliveryResult ещё не приходил;</li>
 *   <li>этапы статуса «Разбираю…» → «Проверяю…» → «Сохраняю…» через
 *       {@link ShowProgress};</li>
 *   <li>парсинг и валидация вне транзакции (LLM — долгий вызов);</li>
 *   <li>в одной транзакции: processed_events (идемпотентность по eventId),
 *       запись расписания, напоминание, журнал команды подтверждения;</li>
 *   <li>подтверждение уходит в канал строго после коммита.</li>
 * </ol>
 *
 * Ошибки (сбой LLM, неполные данные, прошедшее время) отправляются пользователю
 * обычным сообщением без автоудаления. Сбой LLM не фиксируется в
 * processed_events: повторная доставка может снова попробовать (LLM был недоступен).
 * Зона пользователя пока берётся из конфига ({@code app.time-zone}), шаг 9
 * заменит её на зону из профиля.
 */
@Service
public class CreateScheduleUseCase {

    /** Имя консьюмера в processed_events. */
    public static final String CONSUMER_ID = "musicx-core-inbound";

    private static final Logger log = LoggerFactory.getLogger(CreateScheduleUseCase.class);

    private final EventParser eventParser;
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
    public CreateScheduleUseCase(
            EventParser eventParser,
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
        this(eventParser, validator, schedulesRepo, userService, processedEvents, reminders,
                commandJournal, channels, transactions,
                Clock.systemUTC(), ZoneId.of(timeZone), reminderOffset);
    }

    CreateScheduleUseCase(
            EventParser eventParser,
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
        this.eventParser = eventParser;
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

    public void handle(IncomingMessage message) {
        var channel = channels.by(message.channel());
        var conversationId = message.conversationId();

        // Повторная доставка: запись уже есть, LLM не нужен.
        var existing = schedulesRepo.findBySourceChannelAndSourceConversationIdAndSourceMessageId(
                message.channel(), conversationId, message.messageId());
        if (existing.isPresent()) {
            log.info("duplicate delivery, eventId={}", message.eventId());
            resendConfirmation(channel, existing.get());
            return;
        }

        channel.publish(ShowProgress.create(
                message.channel(), conversationId, message.progressRef(), MessageTemplates.PARSING));

        ParsedEvent parsed;
        try {
            parsed = eventParser.parse(message.text(), defaultZone);
        } catch (LlmRequestException e) {
            log.warn("llm failed, eventId={}: {}", message.eventId(), e.getMessage());
            channel.publish(SendMessage.create(
                    message.channel(), conversationId, newCommandId(), MessageTemplates.LLM_FAILED));
            return;
        }

        channel.publish(ShowProgress.create(
                message.channel(), conversationId, message.progressRef(), MessageTemplates.VALIDATING));

        ValidatedSchedule validated;
        try {
            validated = validator.validate(parsed, defaultZone);
        } catch (ScheduleRejectionException e) {
            // Фиксируем событие, чтобы повторная доставка не тратила LLM на тот же отказ.
            markProcessed(message);
            channel.publish(SendMessage.create(
                    message.channel(), conversationId, newCommandId(), e.getMessage()));
            return;
        }

        channel.publish(ShowProgress.create(
                message.channel(), conversationId, message.progressRef(), MessageTemplates.SAVING));

        sendAfterPersist(message, channel, validated);
    }

    private void sendAfterPersist(IncomingMessage message, OutboundChannel channel, ValidatedSchedule validated) {
        var conversationId = message.conversationId();
        var commandId = newCommandId();
        try {
            transactions.executeWithoutResult(status -> {
                if (processedEvents.existsById(new ProcessedEventId(message.eventId(), CONSUMER_ID))) {
                    throw new AlreadyProcessedException();
                }
                // Guard идемпотентности фиксируется в той же транзакции, что и состояние.
                processedEvents.save(processed(message));

                var user = userService.upsertByIdentity(
                        providerOf(message.channel()), message.userRef(), null, null);
                var schedule = saveSchedule(user, message, validated);
                createReminder(schedule, validated);
                commandJournal.save(confirmCommand(commandId, schedule, message));

                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        channel.publish(SendMessage.create(
                                message.channel(), conversationId, commandId,
                                MessageTemplates.confirmation(schedule)));
                    }
                });
            });
        } catch (AlreadyProcessedException | DataIntegrityViolationException e) {
            // Гонка повторной доставки: событие обработал параллельный поток
            // (либо пары уникальных ключей процессed_events/расписания).
            schedulesRepo.findBySourceChannelAndSourceConversationIdAndSourceMessageId(
                            message.channel(), conversationId, message.messageId())
                    .ifPresent(schedule -> resendConfirmation(channel, schedule));
        }
    }

    private userSchedules saveSchedule(userEntity user, IncomingMessage message, ValidatedSchedule validated) {
        var zone = validated.zone();
        var schedule = new userSchedules();
        schedule.setUserId(user.getUserId());
        // Локальные поля для /calendar: фронт продолжает читать их без изменений.
        schedule.setDate(validated.startAt().atZone(zone).toLocalDate());
        schedule.setTime(validated.startAt().atZone(zone).toLocalTime());
        schedule.setTitle(validated.title());
        schedule.setNote(validated.note());
        schedule.setStartAt(validated.startAt());
        schedule.setEndAt(validated.endAt());
        schedule.setTimezone(zone.getId());
        schedule.setStatus(ScheduleStatus.ACTIVE);
        schedule.setSourceChannel(message.channel());
        schedule.setSourceConversationId(message.conversationId());
        schedule.setSourceMessageId(message.messageId());
        schedule.setCreatedAt(Instant.now(clock));
        return schedulesRepo.save(schedule);
    }

    private void createReminder(userSchedules schedule, ValidatedSchedule validated) {
        var remindAt = validated.startAt().minus(reminderOffset);
        var reminder = new Reminder();
        reminder.setScheduleId(schedule.getSceduleId());
        reminder.setKind(ReminderKind.T_MINUS_3H);
        reminder.setRemindAt(remindAt);
        reminder.setStatus(remindAt.isAfter(Instant.now(clock))
                ? ReminderStatus.PENDING
                : ReminderStatus.SKIPPED);
        reminders.save(reminder);
    }

    private CommandJournalEntry confirmCommand(String commandId, userSchedules schedule, IncomingMessage message) {
        var entry = new CommandJournalEntry();
        entry.setCommandId(commandId);
        entry.setType(SendMessage.TYPE);
        entry.setChannel(message.channel());
        entry.setConversationId(message.conversationId());
        entry.setPurpose(CommandPurpose.CONFIRM_SCHEDULE);
        entry.setRelatedId(schedule.getSceduleId());
        entry.setStatus(CommandDeliveryStatus.PENDING);
        entry.setCreatedAt(Instant.now(clock));
        return entry;
    }

    /**
     * Повтор подтверждения нужен, только пока DeliveryResult не пришёл:
     * статус PENDING в журнале. Удалять или напоминать — не эта ветка.
     */
    private void resendConfirmation(OutboundChannel channel, userSchedules schedule) {
        Optional<CommandJournalEntry> journal = commandJournal.findFirstByRelatedIdAndPurposeOrderByCreatedAtDesc(
                schedule.getSceduleId(), CommandPurpose.CONFIRM_SCHEDULE);
        if (journal.isEmpty() || journal.get().getStatus() != CommandDeliveryStatus.PENDING) {
            return;
        }
        // Тот же commandId: транспорт может дедуплицировать повторную отправку.
        channel.publish(SendMessage.create(
                schedule.getSourceChannel(),
                schedule.getSourceConversationId(),
                journal.get().getCommandId(),
                MessageTemplates.confirmation(schedule)));
    }

    private void markProcessed(IncomingMessage message) {
        if (processedEvents.existsById(new ProcessedEventId(message.eventId(), CONSUMER_ID))) {
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
        processed.setConsumer(CONSUMER_ID);
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
