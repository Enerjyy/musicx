package enerjy.musicxService.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface userRepo extends JpaRepository<userEntity, Long> {

    Optional<userEntity> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}