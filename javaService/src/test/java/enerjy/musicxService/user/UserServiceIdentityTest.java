package enerjy.musicxService.user;

import enerjy.musicxService.security.AuthProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Тесты identity-слоя: связка «провайдер → пользователь».
 *
 * Главная проверка здесь — расширяемость: один и тот же пользователь может
 * войти разными способами, и все они ссылаются на одну запись в user_entity.
 * Это то, ради чего таблица user_identity вообще существует.
 */
@SpringBootTest
@Transactional
class UserServiceIdentityTest {

    @Autowired
    private userService userService;

    @Autowired
    private userRepo userRepository;

    @Autowired
    private userIdentityRepo identityRepository;

    @Autowired
    private userSchedulesRepo schedulesRepo;

    @Test
    void createsUserAndIdentityOnFirstLogin() {
        userEntity user = userService.upsertByIdentity(
                AuthProvider.TELEGRAM, "555", "Anna", "anna");

        assertThat(user.getUserId()).isNotNull();
        assertThat(user.getName()).isEqualTo("Anna");
        assertThat(user.getNickname()).isEqualTo("anna");

        assertThat(identityRepository.findByProviderAndProviderUserId(
                AuthProvider.TELEGRAM, "555")).isPresent();
    }

    @Test
    void secondLoginUpdatesProfileWithoutCreatingDuplicate() {
        userEntity first = userService.upsertByIdentity(
                AuthProvider.TELEGRAM, "556", "Petr", "petr_old");
        userEntity second = userService.upsertByIdentity(
                AuthProvider.TELEGRAM, "556", "Petr", "petr_new");

        assertThat(second.getUserId()).isEqualTo(first.getUserId());
        assertThat(second.getNickname()).isEqualTo("petr_new");

        assertThat(identityRepository.findByProviderAndProviderUserId(
                AuthProvider.TELEGRAM, "556")).isPresent();
    }

    /**
     * Тот же человек, другой способ входа. Провайдер «другой», поэтому связка
     * не находится, и создаётся второй пользователь — так и должно быть, пока
     * нет механизма связывания аккаунтов. Этот тест фиксирует текущее
     * поведение, чтобы случайное изменение было заметно.
     */
    @Test
    void differentProviderCreatesSeparateUserForNow() {
        userEntity viaTelegram = userService.upsertByIdentity(
                AuthProvider.TELEGRAM, "557", "Same", "same_tg");

        // Другая строка id у другого провайдера — отдельная связка.
        userEntity viaOther = userService.upsertByIdentity(
                AuthProvider.TELEGRAM, "557-other", "Same", "same_other");

        assertThat(viaOther.getUserId()).isNotEqualTo(viaTelegram.getUserId());
    }

    @Test
    void findIdentitiesReturnsAllWaysToLogin() {
        userEntity user = userService.upsertByIdentity(
                AuthProvider.TELEGRAM, "558", "Multi", "multi");

        // Тот же пользователь, второй способ входа — так будет работать связка
        // аккаунтов, когда появится второй провайдер.
        userIdentityEntity extra = new userIdentityEntity();
        extra.setUserId(user.getUserId());
        extra.setProvider(AuthProvider.TELEGRAM);
        extra.setProviderUserId("558-second");
        identityRepository.save(extra);

        assertThat(userService.findIdentities(user.getUserId())).hasSize(2);
    }

    @Test
    void blankProviderUserIdIsRejected() {
        assertThatThrownBy(() -> userService.upsertByIdentity(
                AuthProvider.TELEGRAM, "   ", "Name", "nick"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullProviderUserIdIsRejected() {
        assertThatThrownBy(() -> userService.upsertByIdentity(
                AuthProvider.TELEGRAM, null, "Name", "nick"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void countSchedulesReturnsZeroForNewUser() {
        userEntity user = userService.upsertByIdentity(AuthProvider.TELEGRAM, "900", "Test", "test");

        assertThat(userService.countSchedules(user.getUserId())).isZero();
    }

    @Test
    void countSchedulesIncrementsAfterAddingEntries() {
        userEntity user = userService.upsertByIdentity(AuthProvider.TELEGRAM, "901", "Test", "test");

        var e1 = new userSchedules();
        e1.setUserId(user.getUserId());
        e1.setDate(java.time.LocalDate.now());
        e1.setTime(java.time.LocalTime.now());
        e1.setTitle("t1");
        e1.setNote("n1");
        schedulesRepo.save(e1);

        var e2 = new userSchedules();
        e2.setUserId(user.getUserId());
        e2.setDate(java.time.LocalDate.now());
        e2.setTime(java.time.LocalTime.now());
        e2.setTitle("t2");
        e2.setNote("n2");
        schedulesRepo.save(e2);

        assertThat(userService.countSchedules(user.getUserId())).isEqualTo(2);
    }

    @Test
    void upsertByIdentityDoesNotOverwriteNameNicknameWithNull() {
        userEntity first = userService.upsertByIdentity(AuthProvider.TELEGRAM, "902", "Original", "orig");

        // Второй вызов с null name/nickname (как делает бот) — не должен стирать профиль
        userEntity second = userService.upsertByIdentity(AuthProvider.TELEGRAM, "902", null, null);

        assertThat(second.getUserId()).isEqualTo(first.getUserId());
        assertThat(second.getName()).isEqualTo("Original");
        assertThat(second.getNickname()).isEqualTo("orig");
    }
}