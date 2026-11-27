package enerjy.musicxService.Logic.schedule;

import enerjy.musicxService.Logic.schedule.parse.Intent;
import enerjy.musicxService.Logic.schedule.parse.ParsedEvent;
import enerjy.musicxService.messaging.MessageTemplates;
import enerjy.musicxService.messaging.contract.Channels;
import enerjy.musicxService.messaging.contract.IncomingMessage;
import enerjy.musicxService.messaging.contract.OutboundCommand;
import enerjy.musicxService.messaging.contract.SendMessage;
import enerjy.musicxService.messaging.contract.ShowProgress;
import enerjy.musicxService.messaging.channel.ChannelRegistry;
import enerjy.musicxService.messaging.channel.OutboundChannel;
import enerjy.musicxService.security.AuthProvider;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Матчинг и правка/удаление записи из чата: 0/1/N кандидатов, пересоздание
 * напоминания при переносе, отмена PENDING при удалении, идемпотентность.
 */
@ExtendWith(MockitoExtension.class)
class ModifyScheduleUseCaseTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    /** 26.11.2026 10:00 UTC = 13:00 МСК. */
    private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");
    private static final Instant START = Instant.parse("2026-11-27T12:00:00Z"); // 15:00 МСК
    /** Новое время после переноса: следующая пятница, 18:00 МСК. */
    private static final Instant MOVED = Instant.parse("2026-12-04T15:00:00Z");

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

    private ModifyScheduleUseCase useCase;

    @BeforeEach
    void setUp() {
        when(channel.id()).thenReturn(Channels.TELEGRAM);
        useCase = new ModifyScheduleUseCase(
                validator, schedulesRepo, userService, processedEvents, reminders,
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
    void updateReschedulesSingleCandidateAndRecreatesReminder() {
        when(userService.upsertByIdentity(eq(AuthProvider.TELEGRAM), eq("42"), isNull(), isNull()))
                .thenReturn(user(7L));
        when(validator.validate(any(), any())).thenReturn(new ValidatedSchedule(
                "Урок гитары", null, MOVED, MOVED.plus(Duration.ofHours(1)), ZONE));
        when(schedulesRepo.findByUserIdAndStatusAndDate(7L, ScheduleStatus.ACTIVE,
                LocalDate.of(2026, 11, 27))).thenReturn(List.of(candidate(11L)));
        when(processedEvents.existsById(any())).thenReturn(false);
        TransactionSynchronizationManager.initSynchronization();

        useCase.handle(message(), parsed(Intent.UPDATE));

        // Прогресс «Проверяю/Сохраняю» идёт до транзакции, отправка — после коммита.
        InOrder order = inOrder(channel);
        order.verify(channel).publish(showProgressWith(MessageTemplates.VALIDATING));
        order.verify(channel).publish(showProgressWith(MessageTemplates.SAVING));

        var saved = ArgumentCaptor.forClass(userSchedules.class);
        verify(schedulesRepo).save(saved.capture());
        var schedule = saved.getValue();
        assertThat(schedule.getSceduleId()).isEqualTo(11L);
        assertThat(schedule.getDate()).isEqualTo(LocalDate.of(2026, 12, 4));
        assertThat(schedule.getTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(schedule.getStartAt()).isEqualTo(MOVED);
        assertThat(schedule.getEndAt()).isEqualTo(MOVED.plus(Duration.ofHours(1)));
        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.ACTIVE);

        // Старые напоминания удалены, новое создано под новое время.
        verify(reminders).deleteByScheduleId(11L);
        var reminder = ArgumentCaptor.forClass(Reminder.class);
        verify(reminders).save(reminder.capture());
        assertThat(reminder.getValue().getScheduleId()).isEqualTo(11L);
        assertThat(reminder.getValue().getKind()).isEqualTo(ReminderKind.T_MINUS_3H);
        assertThat(reminder.getValue().getRemindAt()).isEqualTo(MOVED.minus(Duration.ofHours(3)));
        assertThat(reminder.getValue().getStatus()).isEqualTo(ReminderStatus.PENDING);

        var journal = ArgumentCaptor.forClass(CommandJournalEntry.class);
        verify(commandJournal).save(journal.capture());
        assertThat(journal.getValue().getPurpose()).isEqualTo(CommandPurpose.CONFIRM_UPDATE);
        assertThat(journal.getValue().getRelatedId()).isEqualTo(11L);
        verify(processedEvents).save(any(ProcessedEvent.class));

        assertThat(confirmations()).isEmpty();
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        assertThat(singleConfirmation().text())
                .contains("Запись обновлена: Урок гитары")
                .contains("04.12.2026 18:00")
                .contains("–19:00");
    }

    @Test
    void deleteCancelsSingleCandidateAndRemovesPendingReminders() {
        when(userService.upsertByIdentity(eq(AuthProvider.TELEGRAM), eq("42"), isNull(), isNull()))
                .thenReturn(user(7L));
        when(schedulesRepo.findByUserIdAndStatusAndDate(7L, ScheduleStatus.ACTIVE,
                LocalDate.of(2026, 11, 27))).thenReturn(List.of(candidate(11L)));
        when(processedEvents.existsById(any())).thenReturn(false);
        TransactionSynchronizationManager.initSynchronization();

        useCase.handle(message(), parsed(Intent.DELETE));

        var saved = ArgumentCaptor.forClass(userSchedules.class);
        verify(schedulesRepo).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(ScheduleStatus.CANCELLED);
        // Мягкое удаление: содержимое записи не трогаем.
        assertThat(saved.getValue().getTitle()).isEqualTo("Урок гитары");
        assertThat(saved.getValue().getStartAt()).isEqualTo(START);

        // PENDING-напоминания отменённой записи отправлять нельзя.
        verify(reminders).deleteByScheduleId(11L);
        verify(reminders, never()).save(any());

        var journal = ArgumentCaptor.forClass(CommandJournalEntry.class);
        verify(commandJournal).save(journal.capture());
        assertThat(journal.getValue().getPurpose()).isEqualTo(CommandPurpose.CONFIRM_DELETE);
        // Удаление не валидирует новое время: валидатор не вызывается.
        verify(validator, never()).validate(any(), any());

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        assertThat(singleConfirmation().text())
                .contains("Запись удалена: Урок гитары")
                .contains("27.11.2026 15:00");
    }

    /** Время ±30 минут сужает кандидатов: похожих по названию с другим временем не трогаем. */
    @Test
    void timeWindowFiltersOutDistantCandidates() {
        when(userService.upsertByIdentity(eq(AuthProvider.TELEGRAM), eq("42"), isNull(), isNull()))
                .thenReturn(user(7L));
        when(validator.validate(any(), any())).thenReturn(new ValidatedSchedule(
                "Урок гитары", null, MOVED, MOVED.plus(Duration.ofHours(1)), ZONE));
        var morning = candidate(11L);
        morning.setTime(LocalTime.of(10, 0)); // 5 часов от опорных 15:00 — мимо окна
        when(schedulesRepo.findByUserIdAndStatusAndDate(7L, ScheduleStatus.ACTIVE,
                LocalDate.of(2026, 11, 27))).thenReturn(List.of(morning, candidate(12L)));
        when(processedEvents.existsById(any())).thenReturn(false);
        TransactionSynchronizationManager.initSynchronization();

        useCase.handle(message(), parsed(Intent.UPDATE));

        // Матчинг оставил ровно одну запись в окне ±30 минут — правим её
        // (утренняя 11L отсеяна по времени, осталась 12L в 15:00).
        verify(schedulesRepo).save(savedWithId(12L));
        verify(schedulesRepo, never()).save(savedWithId(11L));
    }

    /** 0 кандидатов — уточняющий вопрос, запись не трогаем. */
    @Test
    void noCandidatesSendsClarifyWithoutTouchingAnything() {
        when(userService.upsertByIdentity(eq(AuthProvider.TELEGRAM), eq("42"), isNull(), isNull()))
                .thenReturn(user(7L));
        when(schedulesRepo.findByUserIdAndStatusAndDate(7L, ScheduleStatus.ACTIVE,
                LocalDate.of(2026, 11, 27))).thenReturn(List.of());
        when(processedEvents.existsById(any())).thenReturn(false);
        TransactionSynchronizationManager.initSynchronization();

        useCase.handle(message(), parsed(Intent.DELETE));

        verify(schedulesRepo, never()).save(any());
        verify(reminders, never()).deleteByScheduleId(anyLong());
        verify(commandJournal, never()).save(argThat(entry ->
                entry instanceof CommandJournalEntry journal
                        && journal.getPurpose() == CommandPurpose.CONFIRM_DELETE));

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        var clarification = singleConfirmation();
        assertThat(clarification.text()).isEqualTo(MessageTemplates.CLARIFY_NOT_FOUND);

        var journal = ArgumentCaptor.forClass(CommandJournalEntry.class);
        verify(commandJournal).save(journal.capture());
        assertThat(journal.getValue().getPurpose()).isEqualTo(CommandPurpose.CLARIFY_SCHEDULE);
    }

    /** Больше одного кандидата — уточняем, чтобы не перенести/удалить не ту запись. */
    @Test
    void severalCandidatesSendsClarifyWithoutTouchingAnything() {
        when(userService.upsertByIdentity(eq(AuthProvider.TELEGRAM), eq("42"), isNull(), isNull()))
                .thenReturn(user(7L));
        // Опорного времени нет в parsed: обе записи дня проходят по названию.
        when(schedulesRepo.findByUserIdAndStatusAndDate(7L, ScheduleStatus.ACTIVE,
                LocalDate.of(2026, 11, 27))).thenReturn(List.of(candidate(11L), candidate(12L)));
        when(processedEvents.existsById(any())).thenReturn(false);
        TransactionSynchronizationManager.initSynchronization();

        useCase.handle(message(), parsedNoTime(Intent.DELETE));

        verify(schedulesRepo, never()).save(any());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        assertThat(singleConfirmation().text()).isEqualTo(MessageTemplates.CLARIFY_SEVERAL);
    }

    /** Опорной даты нет совсем — уточняем, искать не по чему. */
    @Test
    void missingMatchDateSendsClarify() {
        when(userService.upsertByIdentity(eq(AuthProvider.TELEGRAM), eq("42"), isNull(), isNull()))
                .thenReturn(user(7L));
        TransactionSynchronizationManager.initSynchronization();

        useCase.handle(message(), new ParsedEvent(
                true, null, null, null, null, null, Set.of(), false,
                Intent.DELETE, null, null, "урок"));

        verify(schedulesRepo, never()).findByUserIdAndStatusAndDate(anyLong(), any(), any());
        verify(schedulesRepo, never()).save(any());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        assertThat(singleConfirmation().text()).isEqualTo(MessageTemplates.CLARIFY_WHICH);
    }

    /** Повторная доставка после обработки: без LLM-работы и без повторных вопросов. */
    @Test
    void redeliveryOfProcessedEventIsSilent() {
        when(processedEvents.existsById(any())).thenReturn(true);

        useCase.handle(message(), parsed(Intent.DELETE));

        verify(channel, never()).publish(any());
        verify(validator, never()).validate(any(), any());
        verify(userService, never()).upsertByIdentity(any(), anyString(), isNull(), isNull());
        verify(schedulesRepo, never()).findByUserIdAndStatusAndDate(anyLong(), any(), any());
    }

    /** Отказ валидатора нового времени: подсказка и фиксация события. */
    @Test
    void rejectedNewTimeSendsHintAndMarksProcessed() {
        when(validator.validate(any(), any()))
                .thenThrow(new ScheduleRejectionException(ScheduleValidator.HINT_IN_THE_PAST));
        when(processedEvents.existsById(any())).thenReturn(false);

        useCase.handle(message(), parsed(Intent.UPDATE));

        verify(processedEvents).save(any(ProcessedEvent.class));
        verify(schedulesRepo, never()).save(any());
        assertThat(singleConfirmation().text()).contains("уже прошло");
    }

    // --- helpers ---

    private static IncomingMessage message() {
        // Порядок аргументов: channel, conversationId, messageId, userRef.
        return IncomingMessage.create(Channels.TELEGRAM, "42", "2002", "42",
                "перенеси урок гитары на следующую пятницу в 18:00", "ru", "draft-2");
    }

    private static ParsedEvent parsed(Intent intent) {
        // Опора названия — реальная подстрока названия записи («урок» ⊂ «Урок гитары»);
        // «гитара» не подошла бы: в «гитары» последняя буква другая, contains не сработает.
        return new ParsedEvent(
                true, "Урок гитары", null, LocalDateTime.of(2026, 12, 4, 18, 0),
                null, null, Set.of(), false,
                intent, LocalDate.of(2026, 11, 27), LocalTime.of(15, 0), "урок");
    }

    private static ParsedEvent parsedNoTime(Intent intent) {
        return new ParsedEvent(
                true, null, null, null, null, null, Set.of(), false,
                intent, LocalDate.of(2026, 11, 27), null, "урок");
    }

    private static userEntity user(long id) {
        var user = new userEntity();
        user.setUserId(id);
        return user;
    }

    /** Кандидат как его видит репозиторий: живая запись за 27.11 в 15:00 МСК. */
    private static userSchedules candidate(long id) {
        var schedule = new userSchedules();
        schedule.setSceduleId(id);
        schedule.setUserId(7L);
        schedule.setDate(LocalDate.of(2026, 11, 27));
        schedule.setTime(LocalTime.of(15, 0));
        schedule.setTitle("Урок гитары");
        schedule.setStartAt(START);
        schedule.setEndAt(START.plus(Duration.ofHours(1)));
        schedule.setTimezone("Europe/Moscow");
        schedule.setStatus(ScheduleStatus.ACTIVE);
        return schedule;
    }

    private static userSchedules savedWithId(long id) {
        return argThat(argument ->
                argument instanceof userSchedules schedule && id == schedule.getSceduleId());
    }

    private static OutboundCommand showProgressWith(String text) {
        return argThat(argument ->
                argument instanceof ShowProgress progress && text.equals(progress.text()));
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
