package enerjy.musicxService.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface userSchedulesRepo extends JpaRepository<userSchedules, Long> {
}
