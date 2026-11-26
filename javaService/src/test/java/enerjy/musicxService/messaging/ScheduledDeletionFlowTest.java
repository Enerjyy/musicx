package enerjy.musicxService.messaging;

import enerjy.musicxService.messaging.contract.Channels;
import enerjy.musicxService.user.CommandJournalRepo;
import enerjy.musicxService.user.CommandPurpose;
import enerjy.musicxService.user.DeletionStatus;
import enerjy.musicxService.user.ScheduledDeletion;
import enerjy.musicxService.user.ScheduledDeletionRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Планировщик удаления на реальной БД (H2): захват задач, возврат зависших
 * SENDING в очередь и повторный захват. Публикацию DeleteMessage проверяет
 * юнит-тест: здесь после тестовой транзакции команды не отправляются.
 */
@SpringBootTest
@Transactional
class ScheduledDeletionFlowTest {

    @Autowired
    private ScheduledDeletionRepo deletions;

    @Autowired
    private CommandJournalRepo commandJournal;

    @Autowired
    private ScheduledDeletionProcessor processor;

    @Test
    void claimsDueTaskOnRealDatabase() {
        var saved = deletions.saveAndFlush(dueTask());

        processor.processDue();

        var reloaded = deletions.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(DeletionStatus.SENDING);
        assertThat(reloaded.getAttempts()).isEqualTo(1);
        assertThat(reloaded.getClaimedAt()).isNotNull();
        assertThat(reloaded.getCommandId()).isNotNull();

        var journal = commandJournal.findFirstByRelatedIdAndPurposeOrderByCreatedAtDesc(
                reloaded.getId(), CommandPurpose.DELETE_CONFIRMATION);
        assertThat(journal).isPresent();
        assertThat(journal.get().getCommandId()).isEqualTo(reloaded.getCommandId());
        assertThat(journal.get().getType()).isEqualTo("DELETE_MESSAGE");
    }

    @Test
    void stuckSendingIsRescuedAndReclaimedOnNextTick() {
        var stuck = dueTask();
        stuck.setStatus(DeletionStatus.SENDING);
        stuck.setAttempts(1);
        stuck.setClaimedAt(Instant.now().minus(Duration.ofMinutes(15)));
        var saved = deletions.saveAndFlush(stuck);

        processor.processDue();

        var reloaded = deletions.findById(saved.getId()).orElseThrow();
        // Вернули в PENDING и на том же тике забрали снова: delete_at уже в прошлом.
        assertThat(reloaded.getStatus()).isEqualTo(DeletionStatus.SENDING);
        assertThat(reloaded.getAttempts()).isEqualTo(2);
    }

    @Test
    void rescuedButNotYetDueStaysPending() {
        var rescued = dueTask();
        rescued.setStatus(DeletionStatus.SENDING);
        rescued.setClaimedAt(Instant.now().minus(Duration.ofMinutes(15)));
        rescued.setDeleteAt(Instant.now().plusSeconds(120));
        var saved = deletions.saveAndFlush(rescued);

        processor.processDue();

        var reloaded = deletions.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(DeletionStatus.PENDING);
        assertThat(reloaded.getClaimedAt()).isNull();
        assertThat(reloaded.getAttempts()).isZero();
    }

    private static ScheduledDeletion dueTask() {
        var task = new ScheduledDeletion();
        task.setChannel(Channels.TELEGRAM);
        task.setConversationId("42");
        task.setExternalMessageId("555-" + System.nanoTime());
        task.setDeleteAt(Instant.now().minusSeconds(10));
        task.setStatus(DeletionStatus.PENDING);
        task.setAttempts(0);
        task.setCreatedAt(Instant.now().minusSeconds(60));
        return task;
    }
}
