package enerjy.musicxService.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface userSchedulesRepo extends JpaRepository<userSchedules, Long> {
    List<userSchedules> findByUserIdAndDateBetweenOrderByTimeAsc(
            Long userId,
            LocalDate from,
            LocalDate to
    );
}
