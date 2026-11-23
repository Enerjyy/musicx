package enerjy.musicxService.user;

import enerjy.musicxService.security.AuthProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

/**
 * Связка «провайдер → пользователь».
 *
 * Один пользователь может войти разными способами (Telegram и Google), и все
 * они ссылаются на один {@link userEntity#getUserId()}. Запись здесь означает
 * «этот аккаунт у провайдера X с id Y принадлежит нашему пользователю Z».
 *
 * Уникальность пары (provider, providerUserId) — чтобы один и тот же
 * Telegram-аккаунт не создал двух наших пользователей.
 */
@Entity
@Table(
        name = "user_identity",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_identity_provider",
                columnNames = {"provider", "provider_user_id"}
        )
)
@Data
public class userIdentityEntity {

    public userIdentityEntity() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long identityId;

    /** Наш пользователь, которому принадлежит эта связка. */
    @Column(nullable = false)
    private Long userId;

    /** Способ входа. Строка, а не число: новый провайдер не требует миграции. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuthProvider provider;

    /**
     * Идентификатор у провайдера. Строка, а не Long: у Telegram это число,
     * но у Google — непрозрачная строка вида "1234567890-abcdef.apps.googleusercontent.com",
     * у Discord — snowflake. Приводить всё к числу нельзя.
     */
    @Column(nullable = false, length = 255)
    private String providerUserId;
}