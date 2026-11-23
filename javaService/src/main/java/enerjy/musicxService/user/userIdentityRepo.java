package enerjy.musicxService.user;

import enerjy.musicxService.security.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface userIdentityRepo extends JpaRepository<userIdentityEntity, Long> {

    Optional<userIdentityEntity> findByProviderAndProviderUserId(
            AuthProvider provider,
            String providerUserId
    );

    List<userIdentityEntity> findByUserId(Long userId);

    boolean existsByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}