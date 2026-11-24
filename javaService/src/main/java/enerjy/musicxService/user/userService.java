package enerjy.musicxService.user;

import enerjy.musicxService.security.AuthProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Работа с пользователями и их способами входа.
 *
 * Главный метод — {@link #upsertByIdentity}: провайдер не сообщает, новый это
 * пользователь или вернувшийся, поэтому ищем связку (провайдер, id у
 * провайдера) и либо обновляем профиль, либо создаём пользователя и связку.
 *
 * Метод не знает ничего конкретного про Telegram: всё, что ему нужно, —
 * провайдер, идентификатор у провайдера и отображаемое имя. Поэтому второй
 * способ входа добавляется без изменений здесь.
 */
@Service
public class userService {

    private final userRepo userRepository;
    private final userIdentityRepo identityRepository;
    private final userSchedulesRepo schedulesRepo;

    public userService(userRepo userRepository, userIdentityRepo identityRepository, userSchedulesRepo schedulesRepo) {
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
        this.schedulesRepo = schedulesRepo;
    }

    /**
     * Находит пользователя по паре (провайдер, id у провайдера) или создаёт.
     *
     * Обновляем только name и nickname: остальное не трогаем, а имя
     * пользователь мог сменить на стороне провайдера.
     *
     * @param provider       способ входа
     * @param providerUserId идентификатор у провайдера (строка: у разных
     *                       провайдеров разный формат)
     * @param name           отображаемое имя
     * @param nickname       username или аналог
     */
    @Transactional
    public userEntity upsertByIdentity(
            AuthProvider provider,
            String providerUserId,
            String name,
            String nickname
    ) {
        if (providerUserId == null || providerUserId.isBlank()) {
            throw new IllegalArgumentException("providerUserId is required");
        }

        Optional<userIdentityEntity> existingIdentity =
                identityRepository.findByProviderAndProviderUserId(provider, providerUserId);

        if (existingIdentity.isPresent()) {
            userEntity user = findById(existingIdentity.get().getUserId())
                    .orElseThrow(() -> new IllegalStateException(
                            "identity points to missing user: " + existingIdentity.get().getUserId()));

            if (name != null) {
                user.setName(name);
            }
            if (nickname != null) {
                user.setNickname(nickname);
            }
            return userRepository.save(user);
        }

        userEntity fresh = new userEntity();
        fresh.setName(name);
        fresh.setNickname(nickname);
        userEntity saved = userRepository.save(fresh);

        userIdentityEntity identity = new userIdentityEntity();
        identity.setUserId(saved.getUserId());
        identity.setProvider(provider);
        identity.setProviderUserId(providerUserId);
        identityRepository.save(identity);

        return saved;
    }

    public Optional<userEntity> findById(long userId) {
        return userRepository.findByUserId(userId);
    }

    /**
     * Находит или создаёт пользователя по telegram_id.
     *
     * Отдельный метод, чтобы вызывающий код не знал про провайдеров:
     * для календаря важно только «дай мне пользователя с этим telegram_id».
     */
    @Transactional
    public userEntity upsertByTelegramId(long telegramId) {
        return upsertByIdentity(
                AuthProvider.TELEGRAM,
                String.valueOf(telegramId),
                null,
                null
        );
    }

    public List<userEntity> findAll() {
        return userRepository.findAll();
    }

    /** Все способы входа пользователя — для отладки и отображения в профиле. */
    public List<userIdentityEntity> findIdentities(long userId) {
        return identityRepository.findByUserId(userId);
    }

    public long countSchedules(long userId) {
        return schedulesRepo.countByUserId(userId);
    }
}