package enerjy.musicxService.Logic.schedule;

import enerjy.musicxService.LlmRequests.LlmRequestException;
import enerjy.musicxService.Logic.schedule.parse.EventParser;
import enerjy.musicxService.Logic.schedule.parse.ParsedEvent;
import enerjy.musicxService.messaging.MessageTemplates;
import enerjy.musicxService.messaging.contract.Channels;
import enerjy.musicxService.messaging.contract.IncomingMessage;
import enerjy.musicxService.messaging.contract.KafkaMessage;
import enerjy.musicxService.messaging.contract.OutboundCommand;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateScheduleUseCaseTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    /** 26.11.2026 10:00 UTC = 13:00 МСК. */
    private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");
    private static final Instant START = Instant.parse("2026-11-27T12:00:00Z"); // 15:00 МСК

    @Mock
    private EventParser eventParser;
    @Mock
    private ScheduleValidator validator;
    @Mock
    private userSchedulesRepo schedulesRepo;
    @Mock
    private userService userService;
    @Mock
    private ProcessedEventRepo processedEvents;
    @Mock
    private ReminderRepo reminders;
    @Mock
    private CommandJournalRepo commandJournal;
    @Mock
    private OutboundChannel channel;

    private CreateScheduleUseCase useCase;

    @BeforeEach
    void setUp() {
        when(channel.id()).thenReturn(Channels.TELEGRAM);
        useCase = new CreateScheduleUseCase(
                eventParser, validator, schedulesRepo, userService, processedEvents, reminders,
                commandJournal, new ChannelRegistry(List.of(channel)), stubTransactions(),
                Clock.fixed(NOW, ZoneOffset.UTC), ZONE, Duration.ofHours(3));
    }

    @AfterEach
    void clearSynchronizations() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createsScheduleReminderAndSendsConfirmationAfterCommit() {
        when(eventParser.parse(any(), any())).thenReturn(parsedEvent());
        when(validator.validate(any(), any())).thenReturn(validated());
        when(userService.upsertByIdentity(eq(AuthProvider.TELEGRAM), eq("42"), isNull(), isNull()))
                .thenReturn(user(7L));
        when(schedulesRepo.save(any())).thenAnswer(invocation -> {
            var schedule = invocation.getArgument(0, userSchedules.class);
            schedule.setSceduleId(11L);
            return schedule;
        });
        when(processedEvents.existsById(any())).thenReturn(false);

        TransactionSynchronizationManager.initSynchronization();
        useCase.handle(message("завтра в 15:00 урок гитары"));

        // Этапы статуса по порядку; финальное сообщение до коммита не уходит.
        var progress = ArgumentCaptor.forClass(OutboundCommand.class);
        verify(channel, times(3)).publish(progress.capture());
        assertThat(progress.getAllValues())
                .allMatch(ShowProgress.class::isInstance)
                .extracting(value -> ((ShowProgress) value).text())
                .containsExactly(MessageTemplates.PARSING, MessageTemplates.VALIDATING, MessageTemplates.SAVING);
        verify(channel, never()).publish(argThat(SendMessage.class::isInstance));

        // Запись расписания: новые поля и тройка источника.
        var savedSchedule = ArgumentCaptor.forClass(userSchedules.class);
        verify(schedulesRepo).save(savedSchedule.capture());
        var schedule = savedSchedule.getValue();
        assertThat(schedule.getUserId()).isEqualTo(7L);
        assertThat(schedule.getStartAt()).isEqualTo(START);
        assertThat(schedule.getEndAt()).isEqualTo(START.plus(Duration.ofHours(1)));
        assertThat(schedule.getTimezone()).isEqualTo("Europe/Moscow");
        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.ACTIVE);
        assertThat(schedule.getSourceChannel()).isEqualTo(Channels.TELEGRAM);
        assertThat(schedule.getSourceConversationId()).isEqualTo("42");
        assertThat(schedule.getSourceMessageId()).isEqualTo("1001");
        assertThat(schedule.getDate()).isEqualTo(LocalDate.of(2026, 11, 27));
        assertThat(schedule.getTime()).isEqualTo(LocalTime.of(15, 0));

        // Напоминание за 3 часа, статус PENDING.
        var savedReminder = ArgumentCaptor.forClass(Reminder.class);
        verify(reminders).save(savedReminder.capture());
        assertThat(savedReminder.getValue().getScheduleId()).isEqualTo(11L);
        assertThat(savedReminder.getValue().getKind()).isEqualTo(ReminderKind.T_MINUS_3H);
        assertThat(savedReminder.getValue().getRemindAt()).isEqualTo(START.minus(Duration.ofHours(3)));
        assertThat(savedReminder.getValue().getStatus()).isEqualTo(ReminderStatus.PENDING);

        // Отметка обработанного события.
        verify(processedEvents).save(any(ProcessedEvent.class));

        // Журнал: команда подтверждения в статусе PENDING.
        var journal = ArgumentCaptor.forClass(CommandJournalEntry.class);
        verify(commandJournal).save(journal.capture());
        assertThat(journal.getValue().getStatus()).isEqualTo(CommandDeliveryStatus.PENDING);
        assertThat(journal.getValue().getPurpose()).isEqualTo(CommandPurpose.CONFIRM_SCHEDULE);
        assertThat(journal.getValue().getRelatedId()).isEqualTo(11L);

        // Подтверждение уходит только после коммита, с тем же commandId.
        assertThat(confirmations()).isEmpty();

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        var confirmation = singleConfirmation();
        assertThat(confirmation.commandId()).isEqualTo(journal.getValue().getCommandId());
        assertThat(confirmation.conversationId()).isEqualTo("42");
        assertThat(confirmation.text())
                .contains("Запись создана: Урок гитары")
                .contains("27.11.2026 15:00")
                .contains("–16:00")
                .contains("(длительность 1 ч)");
    }

    @Test
    void stagesComeInOrder() {
        when(eventParser.parse(any(), any())).thenReturn(parsedEvent());
        when(validator.validate(any(), any())).thenReturn(validated());
        when(userService.upsertByIdentity(any(), anyString(), isNull(), isNull())).thenReturn(user(7L));
        when(schedulesRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();

        useCase.handle(message("завтра в 15:00"));

        InOrder order = inOrder(channel);
        order.verify(channel).publish(showProgressWith(MessageTemplates.PARSING));
        order.verify(channel).publish(showProgressWith(MessageTemplates.VALIDATING));
        order.verify(channel).publish(showProgressWith(MessageTemplates.SAVING));
    }

    @Test
    void redeliveryResendsConfirmationWithSameCommandIdAndWithoutLlm() {
        when(schedulesRepo.findBySourceChannelAndSourceConversationIdAndSourceMessageId(
                Channels.TELEGRAM, "42", "1001")).thenReturn(Optional.of(createdSchedule(11L)));
        when(commandJournal.findFirstByRelatedIdAndPurposeOrderByCreatedAtDesc(
                11L, CommandPurpose.CONFIRM_SCHEDULE))
                .thenReturn(Optional.of(journal("cmd-1", CommandDeliveryStatus.PENDING)));

        useCase.handle(message("завтра в 15:00"));

        verify(eventParser, never()).parse(any(), any());
        verify(schedulesRepo, never()).save(any());
        verify(commandJournal, never()).save(any());

        var confirmation = singleConfirmation();
        assertThat(confirmation.commandId()).isEqualTo("cmd-1");
        assertThat(confirmation.text()).contains("Запись создана: Урок гитары");
    }

    @Test
    void redeliveryWithDeliveredResultDoesNotResend() {
        when(schedulesRepo.findBySourceChannelAndSourceConversationIdAndSourceMessageId(
                Channels.TELEGRAM, "42", "1001")).thenReturn(Optional.of(createdSchedule(11L)));
        when(commandJournal.findFirstByRelatedIdAndPurposeOrderByCreatedAtDesc(
                11L, CommandPurpose.CONFIRM_SCHEDULE))
                .thenReturn(Optional.of(journal("cmd-1", CommandDeliveryStatus.SENT)));

        useCase.handle(message("завтра в 15:00"));

        verify(channel, never()).publish(any());
    }

    @Test
    void redeliveryWithoutJournalDoesNotResend() {
        when(schedulesRepo.findBySourceChannelAndSourceConversationIdAndSourceMessageId(
                Channels.TELEGRAM, "42", "1001")).thenReturn(Optional.of(createdSchedule(11L)));
        when(commandJournal.findFirstByRelatedIdAndPurposeOrderByCreatedAtDesc(
                11L, CommandPurpose.CONFIRM_SCHEDULE)).thenReturn(Optional.empty());

        useCase.handle(message("завтра в 15:00"));

        verify(channel, never()).publish(any());
    }

    /** Гонка: событие уже обработал параллельный поток, второй повторяет подтверждение. */
    @Test
    void concurrentDuplicateMarksProcessedAndResends() {
        when(schedulesRepo.findBySourceChannelAndSourceConversationIdAndSourceMessageId(
                Channels.TELEGRAM, "42", "1001"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(createdSchedule(11L)));
        when(eventParser.parse(any(), any())).thenReturn(parsedEvent());
        when(validator.validate(any(), any())).thenReturn(validated());
        when(processedEvents.existsById(any())).thenReturn(true);
        when(commandJournal.findFirstByRelatedIdAndPurposeOrderByCreatedAtDesc(
                11L, CommandPurpose.CONFIRM_SCHEDULE))
                .thenReturn(Optional.of(journal("cmd-1", CommandDeliveryStatus.PENDING)));

        useCase.handle(message("завтра в 15:00"));

        verify(schedulesRepo, never()).save(any());
        verify(userService, never()).upsertByIdentity(any(), anyString(), isNull(), isNull());
        assertThat(singleConfirmation().commandId()).isEqualTo("cmd-1");
    }

    @Test
    void rejectionMarksProcessedAndSendsHintWithoutSaving() {
        when(eventParser.parse(any(), any())).thenReturn(parsedEvent());
        when(validator.validate(any(), any()))
                .thenThrow(new ScheduleRejectionException(ScheduleValidator.HINT_IN_THE_PAST));
        when(processedEvents.existsById(any())).thenReturn(false);

        useCase.handle(message("вчера в 15:00"));

        verify(processedEvents).save(any(ProcessedEvent.class));
        verify(schedulesRepo, never()).save(any());
        verify(reminders, never()).save(any());
        assertThat(singleConfirmation().text()).contains("уже прошло");
    }

    @Test
    void llmFailureSendsRetryMessageAndAllowsLaterRetry() {
        when(eventParser.parse(any(), any())).thenThrow(new LlmRequestException("openrouter down"));

        useCase.handle(message("завтра в 15:00"));

        verify(processedEvents, never()).save(any());
        verify(schedulesRepo, never()).save(any());
        assertThat(singleConfirmation().text()).isEqualTo(MessageTemplates.LLM_FAILED);
    }

    @Test
    void reminderIsSkippedWhenCreatedInsideOffset() {
        // Начало через час: напоминание T-3h уже в прошлом — отправлять его не нужно.
        var lateValidated = new ValidatedSchedule(
                "Урок", null, NOW.plus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(2)), ZONE);
        when(eventParser.parse(any(), any())).thenReturn(parsedEvent());
        when(validator.validate(any(), any())).thenReturn(lateValidated);
        when(userService.upsertByIdentity(any(), anyString(), isNull(), isNull())).thenReturn(user(7L));
        when(schedulesRepo.save(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, userSchedules.class).setSceduleId(12L);
            return invocation.getArgument(0);
        });
        TransactionSynchronizationManager.initSynchronization();

        useCase.handle(message("через час"));

        var savedReminder = ArgumentCaptor.forClass(Reminder.class);
        verify(reminders).save(savedReminder.capture());
        assertThat(savedReminder.getValue().getStatus()).isEqualTo(ReminderStatus.SKIPPED);
    }

    @Test
    void unsupportedChannelIsRejected() {
        var message = IncomingMessage.create("WHATSAPP", "1", "10", "2", "текст", null, null);

        assertThatThrownBy(() -> useCase.handle(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WHATSAPP");
        verify(eventParser, never()).parse(any(), any());
    }

    // --- helpers ---

    private static IncomingMessage message(String text) {
        return IncomingMessage.create(Channels.TELEGRAM, "42", "1001", "42", text, "ru", "draft-1");
    }

    private static ParsedEvent parsedEvent() {
        return new ParsedEvent(true, "Урок гитары", "первый", LocalDateTime.of(2026, 11, 27, 15, 0),
                null, null, Set.of(), false);
    }

    private static ValidatedSchedule validated() {
        return new ValidatedSchedule("Урок гитары", "кабинет 5", START, START.plus(Duration.ofHours(1)), ZONE);
    }

    private static userEntity user(long id) {
        var user = new userEntity();
        user.setUserId(id);
        return user;
    }

    private userSchedules createdSchedule(long id) {
        var schedule = new userSchedules();
        schedule.setSceduleId(id);
        schedule.setUserId(7L);
        schedule.setTitle("Урок гитары");
        schedule.setStartAt(START);
        schedule.setEndAt(START.plus(Duration.ofHours(1)));
        schedule.setTimezone("Europe/Moscow");
        schedule.setSourceChannel(Channels.TELEGRAM);
        schedule.setSourceConversationId("42");
        schedule.setSourceMessageId("1001");
        return schedule;
    }

    private static CommandJournalEntry journal(String commandId, CommandDeliveryStatus status) {
        var entry = new CommandJournalEntry();
        entry.setCommandId(commandId);
        entry.setType(SendMessage.TYPE);
        entry.setChannel(Channels.TELEGRAM);
        entry.setConversationId("42");
        entry.setPurpose(CommandPurpose.CONFIRM_SCHEDULE);
        entry.setRelatedId(11L);
        entry.setStatus(status);
        entry.setCreatedAt(NOW);
        return entry;
    }

    private List<SendMessage> confirmations() {
        var captor = ArgumentCaptor.forClass(OutboundCommand.class);
        verify(channel, atLeastOnce()).publish(captor.capture());
        return captor.getAllValues().stream()
                .filter(SendMessage.class::isInstance)
                .map(SendMessage.class::cast)
                .toList();
    }

    private SendMessage singleConfirmation() {
        var sent = confirmations();
        assertThat(sent).hasSize(1);
        return sent.get(0);
    }

    private static OutboundCommand showProgressWith(String text) {
        return argThat(argument ->
                argument instanceof ShowProgress progress && text.equals(progress.text()));
    }

    private static TransactionTemplate stubTransactions() {
        PlatformTransactionManager manager = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
        return new TransactionTemplate(manager);
    }
}
