package enerjy.musicxService.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommandJournalRepo extends JpaRepository<CommandJournalEntry, String> {

    Optional<CommandJournalEntry> findFirstByRelatedIdAndPurposeOrderByCreatedAtDesc(
            Long relatedId,
            CommandPurpose purpose
    );
}
